package io.quizchef.session.application;

import static io.quizchef.session.application.SessionOrchestrationTestFixtures.QUIZ_VERSION;
import static io.quizchef.session.application.SessionOrchestrationTestFixtures.host;
import static io.quizchef.session.application.SessionOrchestrationTestFixtures.sessionHostedBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.quizchef.identity.application.AuthorizationService;
import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.quiz.application.GameplayQuizQuery;
import io.quizchef.quiz.application.PlayableQuizView;
import io.quizchef.quiz.domain.Difficulty;
import io.quizchef.session.domain.GuestParticipantToken;
import io.quizchef.session.domain.ParticipantKey;
import io.quizchef.session.domain.QuestionTimer;
import io.quizchef.session.domain.Session;
import io.quizchef.session.domain.exception.InvalidSessionTransitionException;
import io.quizchef.session.domain.exception.NotSessionHostException;
import io.quizchef.session.infrastructure.persistence.SessionRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ShuffleQuestionsApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-09T10:00:00Z");

    private final SessionRepository sessionRepository = mock(SessionRepository.class);
    private final GameplayQuizQuery gameplayQuizQuery = mock(GameplayQuizQuery.class);
    private final AuthorizationService authorizationService = mock(AuthorizationService.class);
    private final CurrentUser hostUser = host();

    /** Seeded so the drawn order is predictable; production uses SecureRandom. */
    private final ShuffleQuestionsApplicationService service = new ShuffleQuestionsApplicationService(
            sessionRepository, gameplayQuizQuery, authorizationService, new Random(42));

    private final List<UUID> questionIds = List.of(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            UUID.randomUUID(), UUID.randomUUID());

    @Test
    void givesTheSessionAnOrderOfItsOwnWithoutLosingOrInventingQuestions() {
        Session session = lobbySession();

        service.shuffle(hostUser, session.getId());

        // A permutation: the same questions, each once. A shuffle that
        // dropped one would quietly shorten the quiz.
        assertThat(session.questionOrder())
                .hasSameElementsAs(questionIds)
                .hasSize(questionIds.size());
        assertThat(session.questionOrder()).doesNotHaveDuplicates();
    }

    @Test
    void actuallyChangesTheOrderPlayRunsIn() {
        Session session = lobbySession();
        PlayableQuizView quiz = quizOf(questionIds);

        // Before: the authored order, straight from the quiz.
        assertThat(QuestionProgression.orderFor(quiz, session))
                .extracting(PlayableQuizView.PlayableQuestion::questionId)
                .containsExactlyElementsOf(questionIds);

        service.shuffle(hostUser, session.getId());

        // After: the session's own. With this seed it differs, which is the
        // whole point — a "shuffle" that returned the same order would be
        // indistinguishable from doing nothing.
        assertThat(QuestionProgression.orderFor(quiz, session))
                .extracting(PlayableQuizView.PlayableQuestion::questionId)
                .containsExactlyInAnyOrderElementsOf(questionIds)
                .isNotEqualTo(questionIds);

        // And the first question the engine opens is the shuffled first.
        assertThat(QuestionProgression.nextAfter(quiz, session))
                .get()
                .extracting(PlayableQuizView.PlayableQuestion::questionId)
                .isEqualTo(session.questionOrder().getFirst());
    }

    @Test
    void leavesTheQuizAlone() {
        Session session = lobbySession();
        PlayableQuizView quiz = quizOf(questionIds);

        service.shuffle(hostUser, session.getId());

        // The published content is untouched, so another session of the same
        // quiz still starts from the authored order — which is what keeps a
        // past session's questions the ones it actually asked.
        assertThat(quiz.questions())
                .extracting(PlayableQuizView.PlayableQuestion::questionId)
                .containsExactlyElementsOf(questionIds);
        Session untouched = lobbySession();
        assertThat(QuestionProgression.orderFor(quiz, untouched))
                .extracting(PlayableQuizView.PlayableQuestion::questionId)
                .containsExactlyElementsOf(questionIds);
    }

    @Test
    void refusesOnceAQuestionHasBeenPlayed() {
        Session session = lobbySession();
        session.start();
        session.previewQuestion(questionIds.getFirst(),
                QuestionTimer.startingAt(NOW, Duration.ofSeconds(5)));

        // Reordering mid-game would deal a question the room already answered.
        assertThatExceptionOfType(InvalidSessionTransitionException.class)
                .isThrownBy(() -> service.shuffle(hostUser, session.getId()));
    }

    @Test
    void refusesAnyoneButTheSessionsOwnHost() {
        Session session = lobbySession();

        assertThatExceptionOfType(NotSessionHostException.class)
                .isThrownBy(() -> service.shuffle(host(), session.getId()));
    }

    private Session lobbySession() {
        Session session = sessionHostedBy(hostUser, "424242");
        session.openLobby();
        session.registerParticipant(UUID.randomUUID(),
                ParticipantKey.forGuest(GuestParticipantToken.generate()));
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(gameplayQuizQuery.load(QUIZ_VERSION)).thenReturn(quizOf(questionIds));
        return session;
    }

    private static PlayableQuizView quizOf(List<UUID> ids) {
        List<PlayableQuizView.PlayableQuestion> questions = new ArrayList<>();
        ids.forEach(id -> questions.add(new PlayableQuizView.PlayableQuestion(
                id, Difficulty.EASY, Set.of(UUID.randomUUID()), Set.of(UUID.randomUUID()))));
        return new PlayableQuizView(30, questions);
    }
}
