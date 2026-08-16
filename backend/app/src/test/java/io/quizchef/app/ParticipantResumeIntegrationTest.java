package io.quizchef.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

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
import io.quizchef.session.application.ResumeParticipantApplicationService;
import io.quizchef.session.application.ResumeParticipantCommand;
import io.quizchef.session.application.SessionSnapshotView;
import io.quizchef.session.domain.Participant;
import io.quizchef.session.domain.ParticipantAnswer;
import io.quizchef.session.domain.Session;
import io.quizchef.session.domain.SessionPin;
import io.quizchef.session.domain.SessionSettings;
import io.quizchef.session.domain.exception.DisplayNameAlreadyTakenException;
import io.quizchef.session.domain.exception.ParticipantNotFoundException;
import io.quizchef.session.infrastructure.persistence.ParticipantRepository;
import io.quizchef.session.infrastructure.persistence.SessionRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * A player who drops out and comes back must be the <em>same</em>
 * participant, with the same score — and nobody else must be able to become
 * them.
 *
 * <p>Driven against a real database on purpose. Every claim here is about
 * persisted identity or about two threads racing, and neither exists behind
 * a mocked repository: the digest lookup, the roster's uniqueness index, and
 * the session row lock are the mechanisms under test.
 */
@SpringBootTest
@Testcontainers
class ParticipantResumeIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockBean
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private JoinSessionApplicationService joinSessionApplicationService;

    @Autowired
    private ResumeParticipantApplicationService resumeParticipantApplicationService;

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
    private TransactionTemplate transactionTemplate;

    @Autowired
    private EntityManager entityManager;

    private Identity host;
    private UUID quizVersionId;
    private String pin;
    private UUID sessionId;

    @BeforeEach
    void openALobby() {
        host = identityRepository.save(Identity.registered());
        quizVersionId = publishedQuiz(host.reference());
        Lobby lobby = openLobby(nextPin());
        pin = lobby.pin();
        sessionId = lobby.sessionId();
    }

    @Test
    void aReturningPlayerIsTheSameParticipantWithTheSameScore() {
        ParticipantSessionView joined = join("Aman", "hi");
        awardPoints(joined.participantId(), 940);

        SessionSnapshotView resumed = resume(pin, joined.guestParticipantToken());

        assertThat(resumed.participantId()).isEqualTo(joined.participantId());
        assertThat(resumed.participantScore()).isEqualTo(940);
        assertThat(resumed.displayName()).isEqualTo("Aman");
        assertThat(resumed.preferredLanguage()).isEqualTo("hi");
        // One roster entry and one participant row — a resume adds nobody.
        assertThat(participantRepository.findBySessionId(sessionId)).hasSize(1);
        assertThat(rosterSize()).isEqualTo(1);
    }

    @Test
    void repeatedReturnsNeverAccumulateParticipants() {
        ParticipantSessionView joined = join("Aman", "en");

        for (int refresh = 0; refresh < 5; refresh++) {
            resume(pin, joined.guestParticipantToken());
        }

        assertThat(participantRepository.findBySessionId(sessionId)).hasSize(1);
        assertThat(rosterSize()).isEqualTo(1);
    }

    @Test
    void theStoredCredentialIsAHashRatherThanTheTokenItself() {
        ParticipantSessionView joined = join("Aman", "en");
        String issued = joined.guestParticipantToken();

        // What a database dump would show. The token that opens this
        // participant's account is not in it — in either table.
        List<String> stored = transactionTemplate.execute(status -> entityManager
                .createNativeQuery("""
                        select guest_token from quizchef.participants where guest_token is not null
                        union all
                        select guest_token from quizchef.session_participants where guest_token is not null
                        """, String.class)
                .getResultList());

        assertThat(stored).isNotEmpty().doesNotContain(issued);
        assertThat(stored).allSatisfy(value -> assertThat(value).hasSize(64));
        // And the real token still works against those hashes.
        assertThat(resume(pin, issued).participantId()).isEqualTo(joined.participantId());
    }

    @Test
    void aCredentialFromAnotherSessionCannotResumeHere() {
        Lobby other = openLobby(nextPin());
        ParticipantSessionView elsewhere = transactionTemplate.execute(status ->
                joinSessionApplicationService.join(CurrentUser.anonymous(),
                        new JoinSessionCommand(other.pin(), "Aman", "en")));

        assertThatExceptionOfType(ParticipantNotFoundException.class).isThrownBy(() ->
                resume(pin, elsewhere.guestParticipantToken()));
    }

    @Test
    void aCredentialFromAnArchivedSessionCannotResumeIntoTheOneReusingItsPin() {
        // The bug this fix exists for. PINs are unique only among active
        // sessions, so tonight's quiz can be handed last month's code — and
        // a player returning to that familiar code is holding a credential
        // for a game that is over. They must be told to join, never quietly
        // restored into the finished session.
        ParticipantSessionView lastMonth = join("Aman", "en");
        String recycledPin = pin;
        finishAndArchive(sessionId);

        Lobby tonight = openLobby(recycledPin);
        assertThat(tonight.pin()).isEqualTo(recycledPin);

        assertThatExceptionOfType(ParticipantNotFoundException.class).isThrownBy(() ->
                resume(recycledPin, lastMonth.guestParticipantToken()));
    }

    @Test
    void aSecondPlayerCannotTakeANameAlreadyInTheSession() {
        join("Aman", "en");

        // Same name, no credential: the server has no way to know whether
        // this is the same person, and guessing from a name would hand over
        // a stranger's score.
        assertThatExceptionOfType(DisplayNameAlreadyTakenException.class)
                .isThrownBy(() -> join("aman", "en"));
        assertThat(participantRepository.findBySessionId(sessionId)).hasSize(1);
    }

    @Test
    void aValidCredentialResumesRatherThanTrippingTheDuplicateNameRule() {
        ParticipantSessionView joined = join("Aman", "en");
        awardPoints(joined.participantId(), 700);

        // The real Aman returning must never be refused for being Aman.
        SessionSnapshotView resumed = resume(pin, joined.guestParticipantToken());

        assertThat(resumed.participantId()).isEqualTo(joined.participantId());
        assertThat(resumed.participantScore()).isEqualTo(700);
    }

    @Test
    void aSameNameNewcomerInheritsNothingWhenTheyDoJoin() {
        ParticipantSessionView aman = join("Aman", "en");
        awardPoints(aman.participantId(), 900);

        // Once they pick a name of their own, they start at zero — the
        // score belongs to the credential, never to the name.
        ParticipantSessionView impostor = join("Aman 2", "en");

        assertThat(impostor.participantId()).isNotEqualTo(aman.participantId());
        assertThat(scoreOf(impostor.participantId())).isZero();
        assertThat(scoreOf(aman.participantId())).isEqualTo(900);
    }

    @Test
    void simultaneousReturnsConvergeOnOneConnectedParticipant() throws Exception {
        ParticipantSessionView joined = join("Aman", "en");
        awardPoints(joined.participantId(), 500);

        // Two tabs waking together, or a reconnect racing a refresh.
        int attempts = 6;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch start = new CountDownLatch(1);
        ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();
        for (int attempt = 0; attempt < attempts; attempt++) {
            pool.submit(() -> {
                await(start);
                try {
                    resume(pin, joined.guestParticipantToken());
                } catch (Throwable failure) {
                    failures.add(failure);
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        // Nobody is told the resource changed under them, and the room sees
        // one player with the score they had.
        assertThat(failures).isEmpty();
        assertThat(participantRepository.findBySessionId(sessionId)).hasSize(1);
        assertThat(rosterSize()).isEqualTo(1);
        assertThat(scoreOf(joined.participantId())).isEqualTo(500);
    }

    // --- helpers -------------------------------------------------------------

    private record Lobby(String pin, UUID sessionId) {
    }

    private Lobby openLobby(String requestedPin) {
        Session session = Session.create(SessionPin.of(requestedPin), quizVersionId,
                host.reference(), SessionSettings.defaults());
        sessionRepository.saveAndFlush(session);
        openLobbyApplicationService.openLobby(hostUser(host), requestedPin);
        return new Lobby(requestedPin, session.getId());
    }

    private ParticipantSessionView join(String displayName, String language) {
        return transactionTemplate.execute(status ->
                joinSessionApplicationService.join(CurrentUser.anonymous(),
                        new JoinSessionCommand(pin, displayName, language)));
    }

    private SessionSnapshotView resume(String sessionPin, String resumeToken) {
        return resumeParticipantApplicationService.resume(CurrentUser.anonymous(),
                new ResumeParticipantCommand(sessionPin, resumeToken));
    }

    /** Gives a participant a real scored answer, the way gameplay would. */
    private void awardPoints(UUID participantId, int points) {
        transactionTemplate.executeWithoutResult(status -> {
            Participant participant = participantRepository.findById(participantId).orElseThrow();
            participant.recordAnswer(new ParticipantAnswer(UUID.randomUUID(),
                    Set.of(UUID.randomUUID()), LanguageCode.of("en"), Instant.now(), 900, points));
            participantRepository.save(participant);
        });
    }

    private int scoreOf(UUID participantId) {
        return participantRepository.findById(participantId).orElseThrow().getTotalScore();
    }

    private void finishAndArchive(UUID id) {
        transactionTemplate.executeWithoutResult(status -> {
            Session session = sessionRepository.findById(id).orElseThrow();
            session.start();
            session.finish();
            session.archive();
            sessionRepository.saveAndFlush(session);
        });
    }

    private int rosterSize() {
        return transactionTemplate.execute(status ->
                sessionRepository.findById(sessionId).orElseThrow().roster().size());
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    private static CurrentUser hostUser(Identity identity) {
        return CurrentUser.authenticated(identity.getId(), identity.reference().identityType(),
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
        Quiz quiz = Quiz.create(new QuizLocalization(en, "Resume", null), owner);
        quiz.addQuestion(question.getId());
        quiz.publish();
        return quizRepository.save(quiz).getId();
    }

    private static int pinCounter = 700_000;

    private static synchronized String nextPin() {
        return String.valueOf(++pinCounter);
    }
}
