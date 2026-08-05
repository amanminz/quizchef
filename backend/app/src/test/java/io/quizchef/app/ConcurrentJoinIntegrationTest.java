package io.quizchef.app;

import static org.assertj.core.api.Assertions.assertThat;

import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.identity.domain.Identity;
import io.quizchef.identity.domain.IdentityReference;
import io.quizchef.identity.domain.Role;
import io.quizchef.identity.infrastructure.persistence.IdentityRepository;
import io.quizchef.quiz.domain.Difficulty;
import io.quizchef.quiz.domain.LanguageCode;
import io.quizchef.quiz.domain.Option;
import io.quizchef.quiz.domain.Question;
import io.quizchef.quiz.domain.QuestionLocalization;
import io.quizchef.quiz.domain.QuestionType;
import io.quizchef.quiz.domain.Quiz;
import io.quizchef.quiz.domain.QuizLocalization;
import io.quizchef.quiz.infrastructure.persistence.QuestionRepository;
import io.quizchef.quiz.infrastructure.persistence.QuizRepository;
import io.quizchef.session.application.JoinSessionApplicationService;
import io.quizchef.session.application.JoinSessionCommand;
import io.quizchef.session.application.OpenLobbyApplicationService;
import io.quizchef.session.application.ParticipantSessionView;
import io.quizchef.session.application.ReconnectParticipantApplicationService;
import io.quizchef.session.application.ReconnectSessionCommand;
import io.quizchef.session.domain.Session;
import io.quizchef.session.domain.SessionPin;
import io.quizchef.session.domain.SessionSettings;
import io.quizchef.session.domain.event.ParticipantJoinedEvent;
import io.quizchef.session.domain.exception.ParticipantAlreadyJoinedException;
import io.quizchef.session.infrastructure.persistence.ParticipantRepository;
import io.quizchef.session.infrastructure.persistence.SessionRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;

/**
 * Joining is the one moment an entire room acts at once, and it is a
 * read-modify-write of a single shared row: every join appends to the
 * session's roster. This drives the real application service from real
 * threads against a real database, because that contention does not exist
 * in a MockMvc call or behind a mocked repository — the whole failure lived
 * in the transaction boundary.
 *
 * <p>Before the write lock, eight simultaneous joins produced two
 * participants and six {@code conflict.concurrent-modification} failures.
 */
@SpringBootTest
@Testcontainers
@Import(ConcurrentJoinIntegrationTest.JoinEventRecorder.class)
class ConcurrentJoinIntegrationTest {

    /** A full room arriving together. */
    private static final int ROOM_SIZE = 12;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockBean
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private JoinSessionApplicationService joinSessionApplicationService;

    @Autowired
    private ReconnectParticipantApplicationService reconnectParticipantApplicationService;

    @Autowired
    private OpenLobbyApplicationService openLobbyApplicationService;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private ParticipantRepository participantRepository;

    @Autowired
    private IdentityRepository identityRepository;

    @Autowired
    private QuizRepository quizRepository;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private JoinEventRecorder joinEvents;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private String pin;
    private UUID sessionId;

    @BeforeEach
    void openALobby() {
        joinEvents.clear();
        Identity host = identityRepository.save(Identity.registered());
        UUID quizVersionId = publishedQuiz(host.reference());
        Session session = Session.create(SessionPin.of(nextPin()), quizVersionId,
                host.reference(), SessionSettings.defaults());
        sessionRepository.saveAndFlush(session);
        sessionId = session.getId();
        pin = session.getSessionPin().value();
        openLobbyApplicationService.openLobby(hostUser(host), pin);
    }

    @Test
    void everyLegitimateJoinSucceedsWhenTheWholeRoomArrivesAtOnce() throws Exception {
        Outcome outcome = joinConcurrently(ROOM_SIZE, index -> "Guest " + index);

        assertThat(outcome.succeeded()).hasSize(ROOM_SIZE);
        assertThat(outcome.failures()).isEmpty();

        // One roster entry and one participant row each — no duplicates, and
        // nothing half-written by an attempt that lost a race.
        assertThat(rosterSize()).isEqualTo(ROOM_SIZE);
        assertThat(participantRepository.findBySessionId(sessionId)).hasSize(ROOM_SIZE);
        assertThat(participantCount()).isEqualTo(ROOM_SIZE);

        // Exactly one join event each: a losing attempt publishes nothing,
        // because the event is published only after the flush succeeds.
        assertThat(joinEvents.forSession(sessionId)).hasSize(ROOM_SIZE);

        // Every participant is distinct and individually addressable.
        assertThat(outcome.succeeded().stream().map(ParticipantSessionView::participantId).distinct())
                .hasSize(ROOM_SIZE);
    }

    @Test
    void theSameIdentityJoiningTwiceAtOnceIsRefusedAsADuplicate() throws Exception {
        // Two taps of the same button, or two tabs. The uniqueness rule must
        // hold under contention, and the loser must be told it is already in
        // the session — not that some unrelated resource changed.
        Identity player = identityRepository.save(Identity.registered());
        CurrentUser sameUser = CurrentUser.authenticated(
                player.getId(), player.reference().identityType(), Set.of(Role.USER));

        Outcome outcome = joinConcurrently(2, index -> "Same Player", sameUser);

        assertThat(outcome.succeeded()).hasSize(1);
        assertThat(outcome.failures()).hasSize(1);
        assertThat(outcome.failures().getFirst()).isInstanceOf(ParticipantAlreadyJoinedException.class);

        // The refused attempt left nothing behind.
        assertThat(rosterSize()).isEqualTo(1);
        assertThat(participantRepository.findBySessionId(sessionId)).hasSize(1);
        assertThat(joinEvents.forSession(sessionId)).hasSize(1);
    }

    @Test
    void areconnectingParticipantDoesNotBecomeASecondPlayer() throws Exception {
        ParticipantSessionView first = joinSessionApplicationService.join(
                CurrentUser.anonymous(), new JoinSessionCommand(pin, "Returning", "en"));

        // The reported scenario: one device refreshes while others are still
        // arriving. Reconnecting must restore the same participant, and the
        // simultaneous joins must be unaffected by it.
        ExecutorService pool = Executors.newFixedThreadPool(5);
        CountDownLatch start = new CountDownLatch(1);
        ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();
        for (int index = 0; index < 4; index++) {
            int number = index;
            pool.submit(() -> {
                await(start);
                try {
                    joinSessionApplicationService.join(CurrentUser.anonymous(),
                            new JoinSessionCommand(pin, "Late " + number, "en"));
                } catch (Throwable failure) {
                    failures.add(failure);
                }
            });
        }
        pool.submit(() -> {
            await(start);
            try {
                reconnectParticipantApplicationService.reconnect(CurrentUser.anonymous(),
                        new ReconnectSessionCommand(null, first.guestParticipantToken()));
            } catch (Throwable failure) {
                failures.add(failure);
            }
        });
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(failures).isEmpty();
        // Four newcomers plus the one who reconnected — never five plus a
        // duplicate of the returning player.
        assertThat(rosterSize()).isEqualTo(5);
        assertThat(participantRepository.findBySessionId(sessionId)).hasSize(5);
        assertThat(joinEvents.forSession(sessionId)).hasSize(5);
    }

    // --- helpers -------------------------------------------------------------

    private record Outcome(List<ParticipantSessionView> succeeded, List<Throwable> failures) {
    }

    private Outcome joinConcurrently(int count, java.util.function.IntFunction<String> naming)
            throws Exception {
        return joinConcurrently(count, naming, null);
    }

    /**
     * Fires {@code count} joins from separate threads released together, so
     * they genuinely contend rather than queue behind one another.
     */
    private Outcome joinConcurrently(int count, java.util.function.IntFunction<String> naming,
                                     CurrentUser as) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(count);
        CountDownLatch start = new CountDownLatch(1);
        ConcurrentLinkedQueue<ParticipantSessionView> succeeded = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();

        for (int index = 0; index < count; index++) {
            int number = index;
            pool.submit(() -> {
                await(start);
                try {
                    succeeded.add(joinSessionApplicationService.join(
                            as == null ? CurrentUser.anonymous() : as,
                            new JoinSessionCommand(pin, naming.apply(number), "en")));
                } catch (Throwable failure) {
                    failures.add(failure);
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        return new Outcome(List.copyOf(succeeded), List.copyOf(failures));
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    /**
     * The roster is a lazy collection, and this test deliberately runs
     * outside a transaction so its threads genuinely contend — so the
     * assertions read it inside one of their own.
     */
    private int rosterSize() {
        return transactionTemplate.execute(status ->
                sessionRepository.findById(sessionId).orElseThrow().roster().size());
    }

    private int participantCount() {
        return transactionTemplate.execute(status ->
                sessionRepository.findById(sessionId).orElseThrow().participantCount());
    }

    private static CurrentUser hostUser(Identity host) {
        return CurrentUser.authenticated(host.getId(), host.reference().identityType(),
                Set.of(Role.USER, Role.QUIZ_MASTER));
    }

    private UUID publishedQuiz(IdentityReference owner) {
        LanguageCode en = LanguageCode.of("en");
        Option correct = Option.of(true, 1);
        Option wrong = Option.of(false, 2);
        Question question = questionRepository.save(Question.create(
                new QuestionLocalization(en, "Q", "Prompt", "Because"),
                owner, QuestionType.TRUE_FALSE, Difficulty.EASY,
                List.of(correct, wrong),
                List.of(correct.localized(en, "True"), wrong.localized(en, "False"))));
        Quiz quiz = Quiz.create(new QuizLocalization(en, "Concurrency", null), owner);
        quiz.addQuestion(question.getId());
        quiz.publish();
        return quizRepository.save(quiz).getId();
    }

    private static int pinCounter = 100_000;

    private static synchronized String nextPin() {
        return String.valueOf(++pinCounter);
    }

    /**
     * Counts the join events actually published. A join that loses a race
     * must not announce itself — the roster would disagree with the wire.
     */
    @TestConfiguration
    @Component
    static class JoinEventRecorder {

        private final ConcurrentLinkedQueue<ParticipantJoinedEvent> events = new ConcurrentLinkedQueue<>();

        @EventListener
        void on(ParticipantJoinedEvent event) {
            events.add(event);
        }

        void clear() {
            events.clear();
        }

        List<ParticipantJoinedEvent> forSession(UUID sessionId) {
            return events.stream().filter(event -> event.sessionId().equals(sessionId)).toList();
        }
    }
}
