package io.quizchef.session.application;

import static io.quizchef.session.application.SessionOrchestrationTestFixtures.QUIZ_VERSION;
import static io.quizchef.session.application.SessionOrchestrationTestFixtures.host;
import static io.quizchef.session.application.SessionOrchestrationTestFixtures.sessionHostedBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.quizchef.quiz.application.GameplayQuizQuery;
import io.quizchef.quiz.application.PlayableQuizView;
import io.quizchef.quiz.domain.Difficulty;
import io.quizchef.session.domain.GuestParticipantToken;
import io.quizchef.session.domain.LeaderboardEntry;
import io.quizchef.session.domain.LeaderboardService;
import io.quizchef.session.domain.ParticipantKey;
import io.quizchef.session.domain.QuestionTimer;
import io.quizchef.session.domain.Session;
import io.quizchef.session.domain.SessionPhase;
import io.quizchef.session.domain.SessionState;
import io.quizchef.session.domain.exception.ResultsNotAvailableException;
import io.quizchef.session.infrastructure.persistence.ParticipantRepository;
import io.quizchef.session.infrastructure.persistence.SessionRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SessionResultsQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-18T10:00:00Z");
    private static final UUID QUESTION = UUID.randomUUID();

    private final SessionRepository sessionRepository = mock(SessionRepository.class);
    private final ParticipantRepository participantRepository = mock(ParticipantRepository.class);
    private final LeaderboardService leaderboardService = mock(LeaderboardService.class);
    private final GameplayQuizQuery gameplayQuizQuery = mock(GameplayQuizQuery.class);
    private final SessionResultsQueryService service = new SessionResultsQueryService(
            sessionRepository, participantRepository, leaderboardService, gameplayQuizQuery);

    @Test
    void refusesWhileAQuestionIsStillBeingPlayed() {
        Session session = inProgressSession();
        // QUESTION_OPEN: standings would leak who answered correctly pre-reveal.
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));

        assertThatExceptionOfType(ResultsNotAvailableException.class)
                .isThrownBy(() -> service.results(session.getId()));

        session.closeQuestion();
        assertThatExceptionOfType(ResultsNotAvailableException.class)
                .isThrownBy(() -> service.results(session.getId()));
    }

    @Test
    void servesFreshStandingsOnceTheAnswerIsRevealed() {
        Session session = inProgressSession();
        session.closeQuestion();
        session.revealAnswer();
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(leaderboardService.rank(anyList(), any())).thenReturn(List.of(
                new LeaderboardEntry(UUID.randomUUID(), "Ann", 750, 1)));
        when(gameplayQuizQuery.load(QUIZ_VERSION)).thenReturn(quizWithQuestions(2));

        SessionResultsView view = service.results(session.getId());

        assertThat(view.state()).isEqualTo(SessionState.IN_PROGRESS);
        assertThat(view.currentPhase()).isEqualTo(SessionPhase.ANSWER_REVEALED);
        assertThat(view.totalQuestions()).isEqualTo(2);
        assertThat(view.participantCount()).isEqualTo(1);
        assertThat(view.entries()).extracting(LeaderboardEntry::displayName).containsExactly("Ann");
    }

    @Test
    void staysReadableAfterTheSessionFinishes() {
        Session session = inProgressSession();
        session.closeQuestion();
        session.revealAnswer();
        session.showLeaderboard();
        session.finish();
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(leaderboardService.rank(anyList(), any())).thenReturn(List.of());
        when(gameplayQuizQuery.load(QUIZ_VERSION)).thenReturn(quizWithQuestions(2));

        SessionResultsView view = service.results(session.getId());

        assertThat(view.state()).isEqualTo(SessionState.FINISHED);
        assertThat(view.currentPhase()).isNull();
    }

    private static Session inProgressSession() {
        Session session = sessionHostedBy(host(), "424242");
        session.openLobby();
        session.registerParticipant(UUID.randomUUID(),
                ParticipantKey.forGuest(GuestParticipantToken.generate()));
        session.start();
        session.openQuestion(QUESTION, QuestionTimer.startingAt(NOW, Duration.ofSeconds(30)));
        return session;
    }

    private static PlayableQuizView quizWithQuestions(int count) {
        List<PlayableQuizView.PlayableQuestion> questions = java.util.stream.IntStream
                .range(0, count)
                .mapToObj(index -> new PlayableQuizView.PlayableQuestion(
                        UUID.randomUUID(), Difficulty.EASY, Set.of(UUID.randomUUID()),
                        Set.of(UUID.randomUUID())))
                .toList();
        return new PlayableQuizView(30, questions);
    }
}
