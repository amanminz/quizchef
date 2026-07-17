package io.quizchef.session.application;

import static io.quizchef.session.application.SessionOrchestrationTestFixtures.QUIZ_VERSION;
import static io.quizchef.session.application.SessionOrchestrationTestFixtures.host;
import static io.quizchef.session.application.SessionOrchestrationTestFixtures.sessionHostedBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.quizchef.quiz.application.GameplayQuestionContentQuery;
import io.quizchef.quiz.application.GameplayQuizQuery;
import io.quizchef.quiz.application.PlayableQuestionContentView;
import io.quizchef.quiz.application.PlayableQuizView;
import io.quizchef.quiz.domain.Difficulty;
import io.quizchef.quiz.domain.QuestionType;
import io.quizchef.session.domain.GuestParticipantToken;
import io.quizchef.session.domain.ParticipantKey;
import io.quizchef.session.domain.QuestionTimer;
import io.quizchef.session.domain.Session;
import io.quizchef.session.domain.SessionPhase;
import io.quizchef.session.domain.exception.NoCurrentQuestionException;
import io.quizchef.session.infrastructure.persistence.SessionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CurrentQuestionQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-17T10:00:00Z");
    private static final UUID QUESTION_1 = UUID.randomUUID();
    private static final UUID QUESTION_2 = UUID.randomUUID();
    private static final UUID CORRECT_OPTION = UUID.randomUUID();
    private static final UUID WRONG_OPTION = UUID.randomUUID();

    private final SessionRepository sessionRepository = mock(SessionRepository.class);
    private final GameplayQuizQuery gameplayQuizQuery = mock(GameplayQuizQuery.class);
    private final GameplayQuestionContentQuery contentQuery = mock(GameplayQuestionContentQuery.class);
    private final CurrentQuestionQueryService service = new CurrentQuestionQueryService(
            sessionRepository, gameplayQuizQuery, contentQuery, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void refusesWhileNoQuestionIsInPlay() {
        Session session = sessionHostedBy(host(), "424242");
        session.openLobby();
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));

        assertThatExceptionOfType(NoCurrentQuestionException.class)
                .isThrownBy(() -> service.currentQuestion(session.getId()));
    }

    @Test
    void servesTheOpenQuestionWithTimeButWithoutCorrectness() {
        Session session = inProgressWithOpenQuestion();
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(gameplayQuizQuery.load(QUIZ_VERSION)).thenReturn(twoQuestionQuiz());
        when(contentQuery.content(QUESTION_1)).thenReturn(contentOf(QUESTION_1));

        CurrentQuestionView view = service.currentQuestion(session.getId());

        assertThat(view.phase()).isEqualTo(SessionPhase.QUESTION_OPEN);
        assertThat(view.questionNumber()).isEqualTo(1);
        assertThat(view.totalQuestions()).isEqualTo(2);
        // 20 of the 30 seconds remain on the fixed clock
        assertThat(view.remainingMillis()).isEqualTo(20_000L);
        assertThat(view.endsAt()).isEqualTo(NOW.plusSeconds(20));
        assertThat(view.correctOptionIds()).isNull();
        // The explanation is reveal-time material — withheld while open.
        assertThat(view.content().localizations().getFirst().explanation()).isNull();
    }

    @Test
    void revealsCorrectOptionsOnlyOnceThePhaseHas() {
        Session session = inProgressWithOpenQuestion();
        session.closeQuestion();
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(gameplayQuizQuery.load(QUIZ_VERSION)).thenReturn(twoQuestionQuiz());
        when(contentQuery.content(QUESTION_1)).thenReturn(contentOf(QUESTION_1));

        CurrentQuestionView closed = service.currentQuestion(session.getId());
        assertThat(closed.correctOptionIds()).isNull();
        assertThat(closed.endsAt()).isNull();
        assertThat(closed.remainingMillis()).isZero();

        session.revealAnswer();
        CurrentQuestionView revealed = service.currentQuestion(session.getId());
        assertThat(revealed.correctOptionIds()).containsExactly(CORRECT_OPTION);
        assertThat(revealed.content().localizations().getFirst().explanation())
                .isEqualTo("Jonah 1:17");
    }

    private static Session inProgressWithOpenQuestion() {
        Session session = sessionHostedBy(host(), "424242");
        session.openLobby();
        session.registerParticipant(UUID.randomUUID(),
                ParticipantKey.forGuest(GuestParticipantToken.generate()));
        session.start();
        // Opened 10 seconds ago with a 30-second limit.
        session.openQuestion(QUESTION_1,
                QuestionTimer.startingAt(NOW.minusSeconds(10), Duration.ofSeconds(30)));
        return session;
    }

    private static PlayableQuizView twoQuestionQuiz() {
        return new PlayableQuizView(30, List.of(
                new PlayableQuizView.PlayableQuestion(QUESTION_1, Difficulty.EASY,
                        Set.of(CORRECT_OPTION), Set.of(CORRECT_OPTION, WRONG_OPTION)),
                new PlayableQuizView.PlayableQuestion(QUESTION_2, Difficulty.EASY,
                        Set.of(CORRECT_OPTION), Set.of(CORRECT_OPTION, WRONG_OPTION))));
    }

    private static PlayableQuestionContentView contentOf(UUID questionId) {
        return new PlayableQuestionContentView(questionId, QuestionType.TRUE_FALSE, "en",
                List.of(new PlayableQuestionContentView.PlayableOptionView(CORRECT_OPTION, 1),
                        new PlayableQuestionContentView.PlayableOptionView(WRONG_OPTION, 2)),
                List.of(new PlayableQuestionContentView.PlayableLocalizationView("en", "Prompt 1",
                        "Jonah 1:17",
                        List.of(new PlayableQuestionContentView.PlayableOptionTextView(CORRECT_OPTION, "True"),
                                new PlayableQuestionContentView.PlayableOptionTextView(WRONG_OPTION, "False")))));
    }
}
