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
import io.quizchef.session.application.CloseQuestionApplicationService;
import io.quizchef.session.application.JoinSessionApplicationService;
import io.quizchef.session.application.JoinSessionCommand;
import io.quizchef.session.application.OpenLobbyApplicationService;
import io.quizchef.session.application.OpenQuestionApplicationService;
import io.quizchef.session.application.StartQuestionApplicationService;
import io.quizchef.session.application.StartSessionApplicationService;
import io.quizchef.session.domain.Session;
import io.quizchef.session.domain.SessionPhase;
import io.quizchef.session.domain.SessionPin;
import io.quizchef.session.domain.SessionSettings;
import io.quizchef.session.domain.event.QuestionClosedEvent;
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
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The question timer and the host both close questions, and at the end of a
 * countdown they can arrive together. Exactly one transition must win: the
 * loser must not close a second time, publish a second event, or roll the
 * winner back.
 *
 * <p>Real threads against a real database, because the whole question is
 * what two transactions do to one row. The guards under test
 * ({@code acceptsAnswersFor}, and the preview-phase check on the open path)
 * are only meaningful at that boundary.
 */
@SpringBootTest
@Testcontainers
@Import(TimerVersusHostCommandIntegrationTest.ClosedEventRecorder.class)
class TimerVersusHostCommandIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockBean
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private CloseQuestionApplicationService closeQuestionApplicationService;

    @Autowired
    private OpenQuestionApplicationService openQuestionApplicationService;

    @Autowired
    private StartQuestionApplicationService startQuestionApplicationService;

    @Autowired
    private StartSessionApplicationService startSessionApplicationService;

    @Autowired
    private OpenLobbyApplicationService openLobbyApplicationService;

    @Autowired
    private JoinSessionApplicationService joinSessionApplicationService;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private IdentityRepository identityRepository;

    @Autowired
    private QuizRepository quizRepository;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private ClosedEventRecorder closedEvents;

    private CurrentUser host;
    private UUID sessionId;
    private UUID questionId;

    @BeforeEach
    void openAQuestion() {
        closedEvents.clear();
        Identity hostIdentity = identityRepository.save(Identity.registered());
        host = CurrentUser.authenticated(hostIdentity.getId(),
                hostIdentity.reference().identityType(), Set.of(Role.USER, Role.QUIZ_MASTER));
        UUID quizVersionId = publishedQuiz(hostIdentity.reference());

        Session session = Session.create(SessionPin.of(nextPin()), quizVersionId,
                hostIdentity.reference(), SessionSettings.defaults());
        sessionRepository.saveAndFlush(session);
        sessionId = session.getId();
        String pin = session.getSessionPin().value();

        openLobbyApplicationService.openLobby(host, pin);
        joinSessionApplicationService.join(CurrentUser.anonymous(),
                new JoinSessionCommand(pin, "Player", "en"));
        startSessionApplicationService.start(host, sessionId);
        startQuestionApplicationService.start(host, sessionId);

        // The engine opens into the reading period; drive it to open for
        // answers the way the scheduler would, so the race under test is the
        // close race rather than the preview one.
        questionId = currentQuestionId();
        openQuestionApplicationService.openIfPreviewExpired(sessionId, questionId, 30);
        assertThat(currentPhase()).isEqualTo(SessionPhase.QUESTION_OPEN);
    }

    @Test
    void onlyOneCloseWinsWhenTheTimerAndTheHostArriveTogether() throws Exception {
        Outcome outcome = race(
                () -> closeQuestionApplicationService.close(host, sessionId),
                () -> closeQuestionApplicationService.closeIfExpired(sessionId, questionId));

        // Whoever lost may have thrown — an invalid transition or a version
        // conflict, both meaning "already done". What must not happen is two
        // closes taking effect.
        assertThat(currentPhase())
                .as("the question is closed exactly once, whoever got there first")
                .isEqualTo(SessionPhase.QUESTION_CLOSED);
        assertThat(closedEvents.forSession(sessionId))
                .as("one transition, one event — a losing attempt announces nothing")
                .hasSize(1);
        assertThat(outcome.failures().size())
                .as("at most one of the two paths fails, never both")
                .isLessThanOrEqualTo(1);
    }

    @Test
    void aTimerThatFiresAfterPlayMovesOnChangesNothing() {
        // The scheduled close for question one, arriving after the host has
        // already closed and revealed it. The guard is on the question id and
        // the phase together, so a late callback cannot reach into a later
        // question's state.
        closeQuestionApplicationService.close(host, sessionId);
        closedEvents.clear();

        closeQuestionApplicationService.closeIfExpired(sessionId, questionId);

        assertThat(currentPhase()).isEqualTo(SessionPhase.QUESTION_CLOSED);
        assertThat(closedEvents.forSession(sessionId))
                .as("a stale timer publishes nothing at all")
                .isEmpty();
    }

    @Test
    void aStalePreviewCallbackCannotReopenAClosedQuestion() {
        closeQuestionApplicationService.close(host, sessionId);

        // The preview-end callback for this same question, arriving late.
        var reopened = openQuestionApplicationService.openIfPreviewExpired(sessionId, questionId, 30);

        assertThat(reopened).as("a late preview callback must not reopen answering").isEmpty();
        assertThat(currentPhase()).isEqualTo(SessionPhase.QUESTION_CLOSED);
    }

    @Test
    void twoHostClicksCloseOnlyOnce() throws Exception {
        // A double-tapped button, or two host tabs.
        Outcome outcome = race(
                () -> closeQuestionApplicationService.close(host, sessionId),
                () -> closeQuestionApplicationService.close(host, sessionId));

        assertThat(currentPhase()).isEqualTo(SessionPhase.QUESTION_CLOSED);
        assertThat(closedEvents.forSession(sessionId)).hasSize(1);
        assertThat(outcome.failures())
                .as("the second click is refused rather than closing again")
                .hasSize(1);
    }

    // --- helpers -------------------------------------------------------------

    private record Outcome(List<Throwable> failures) {
    }

    private Outcome race(Runnable first, Runnable second) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();
        for (Runnable action : List.of(first, second)) {
            pool.submit(() -> {
                try {
                    start.await();
                    action.run();
                } catch (Throwable failure) {
                    failures.add(failure);
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        return new Outcome(List.copyOf(failures));
    }

    private SessionPhase currentPhase() {
        return transactionTemplate.execute(status ->
                sessionRepository.findById(sessionId).orElseThrow().getCurrentPhase());
    }

    private UUID currentQuestionId() {
        return transactionTemplate.execute(status ->
                sessionRepository.findById(sessionId).orElseThrow().getCurrentQuestionId());
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
        Quiz quiz = Quiz.create(new QuizLocalization(en, "Race", null), owner);
        quiz.addQuestion(question.getId());
        quiz.publish();
        return quizRepository.save(quiz).getId();
    }

    private static int pinCounter = 300_000;

    private static synchronized String nextPin() {
        return String.valueOf(++pinCounter);
    }

    /** Counts the closes that actually took effect. */
    @TestConfiguration
    @Component
    static class ClosedEventRecorder {

        private final ConcurrentLinkedQueue<QuestionClosedEvent> events = new ConcurrentLinkedQueue<>();

        @EventListener
        void on(QuestionClosedEvent event) {
            events.add(event);
        }

        void clear() {
            events.clear();
        }

        List<QuestionClosedEvent> forSession(UUID sessionId) {
            return events.stream().filter(event -> event.sessionId().equals(sessionId)).toList();
        }
    }
}
