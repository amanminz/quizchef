package io.quizchef.session.application;

import static io.quizchef.session.application.SessionOrchestrationTestFixtures.sessionQuizQuery;
import static io.quizchef.session.application.SessionOrchestrationTestFixtures.QUIZ_VERSION;
import static io.quizchef.session.application.SessionOrchestrationTestFixtures.host;
import static io.quizchef.session.application.SessionOrchestrationTestFixtures.sessionHostedBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.quizchef.identity.application.AuthorizationService;
import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.quiz.application.GameplayQuizQuery;
import io.quizchef.quiz.application.PlayableQuizView;
import io.quizchef.quiz.domain.Difficulty;
import io.quizchef.quiz.domain.LanguageCode;
import io.quizchef.session.domain.GuestParticipantToken;
import io.quizchef.session.domain.LeaderboardEntry;
import io.quizchef.session.domain.LeaderboardService;
import io.quizchef.session.domain.Participant;
import io.quizchef.session.domain.ParticipantAnswer;
import io.quizchef.session.domain.ParticipantKey;
import io.quizchef.session.domain.QuestionTimer;
import io.quizchef.session.domain.Session;
import io.quizchef.session.domain.SessionPhase;
import io.quizchef.session.domain.SessionState;
import io.quizchef.session.domain.exception.NotSessionHostException;
import io.quizchef.session.domain.exception.ParticipantNotFoundException;
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
    private final AuthorizationService authorizationService = mock(AuthorizationService.class);
    private final SessionResultsQueryService service = new SessionResultsQueryService(
            sessionRepository, participantRepository, leaderboardService,
            sessionQuizQuery(gameplayQuizQuery),
            authorizationService);

    private final CurrentUser hostUser = host();

    @Test
    void refusesWhileAQuestionIsStillBeingPlayed() {
        Session session = inProgressSession(hostUser);
        // QUESTION_OPEN: standings would leak who answered correctly pre-reveal.
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));

        assertThatExceptionOfType(ResultsNotAvailableException.class)
                .isThrownBy(() -> service.results(hostUser, session.getId()));

        session.closeQuestion();
        assertThatExceptionOfType(ResultsNotAvailableException.class)
                .isThrownBy(() -> service.results(hostUser, session.getId()));
    }

    @Test
    void servesFreshStandingsToTheHostOnceTheAnswerIsRevealed() {
        Session session = inProgressSession(hostUser);
        session.closeQuestion();
        session.revealAnswer();
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(leaderboardService.rank(anyList(), any())).thenReturn(List.of(
                new LeaderboardEntry(UUID.randomUUID(), "Ann", 750, 1)));
        when(gameplayQuizQuery.load(QUIZ_VERSION)).thenReturn(quizWithQuestions(2));

        SessionResultsView view = service.results(hostUser, session.getId());

        assertThat(view.state()).isEqualTo(SessionState.IN_PROGRESS);
        assertThat(view.currentPhase()).isEqualTo(SessionPhase.ANSWER_REVEALED);
        assertThat(view.totalQuestions()).isEqualTo(2);
        assertThat(view.participantCount()).isEqualTo(1);
        assertThat(view.entries()).extracting(LeaderboardEntry::displayName).containsExactly("Ann");
    }

    @Test
    void refusesTheFullStandingsToAnyoneButTheHost() {
        Session session = inProgressSession(hostUser);
        session.closeQuestion();
        session.revealAnswer();
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));

        // Authorized for QUIZ_HOST, but hosting someone else's session:
        // ownership is a separate check (SessionHostPolicy).
        assertThatExceptionOfType(NotSessionHostException.class)
                .isThrownBy(() -> service.results(host(), session.getId()));
    }

    @Test
    void staysReadableAfterTheSessionFinishes() {
        Session session = inProgressSession(hostUser);
        session.closeQuestion();
        session.revealAnswer();
        session.showLeaderboard();
        session.finish();
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(leaderboardService.rank(anyList(), any())).thenReturn(List.of());
        when(gameplayQuizQuery.load(QUIZ_VERSION)).thenReturn(quizWithQuestions(2));

        SessionResultsView view = service.results(hostUser, session.getId());

        assertThat(view.state()).isEqualTo(SessionState.FINISHED);
        assertThat(view.currentPhase()).isNull();
    }

    @Test
    void personalResultCarriesTheirOwnProgressAndNoRankAtAll() {
        Session session = inProgressSession(hostUser);
        session.closeQuestion();
        session.revealAnswer();
        Participant me = player("Me", 750, 750);
        Participant rival = player("Rival", 900, 900);
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(participantRepository.findBySessionId(session.getId()))
                .thenReturn(List.of(rival, me));
        when(gameplayQuizQuery.load(QUIZ_VERSION)).thenReturn(quizWithQuestions(2));

        ParticipantResultView view = service.personalResult(session.getId(), me.getId());

        assertThat(view.participantId()).isEqualTo(me.getId());
        assertThat(view.displayName()).isEqualTo("Me");
        assertThat(view.score()).isEqualTo(750);
        assertThat(view.pointsEarned()).isEqualTo(750);
        assertThat(view.totalQuestions()).isEqualTo(2);

        // The projection has no rank on it to leak — and the read never
        // ranked anyone in the first place, so there was none in scope.
        assertThat(view).hasNoNullFieldsOrProperties();
        assertThat(ParticipantResultView.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .doesNotContain("rank");
        verifyNoInteractions(leaderboardService);
    }

    @Test
    void personalResultReportsZeroPointsForAQuestionTheyDidNotAnswer() {
        Session session = inProgressSession(hostUser);
        session.closeQuestion();
        session.revealAnswer();
        Participant me = player("Me", 0, null);
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(participantRepository.findBySessionId(session.getId())).thenReturn(List.of(me));
        when(gameplayQuizQuery.load(QUIZ_VERSION)).thenReturn(quizWithQuestions(2));

        ParticipantResultView view = service.personalResult(session.getId(), me.getId());

        // An honest zero, not a missing value the client has to interpret.
        assertThat(view.pointsEarned()).isZero();
        assertThat(view.score()).isZero();
    }

    @Test
    void personalResultIsPhaseGatedLikeTheStandings() {
        Session session = inProgressSession(hostUser);
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));

        assertThatExceptionOfType(ResultsNotAvailableException.class)
                .isThrownBy(() -> service.personalResult(session.getId(), UUID.randomUUID()));
    }

    @Test
    void personalResultForAnUnknownParticipantIsNotFound() {
        Session session = inProgressSession(hostUser);
        session.closeQuestion();
        session.revealAnswer();
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(participantRepository.findBySessionId(session.getId()))
                .thenReturn(List.of(player("Ann", 750, 750)));
        when(gameplayQuizQuery.load(QUIZ_VERSION)).thenReturn(quizWithQuestions(2));

        assertThatExceptionOfType(ParticipantNotFoundException.class)
                .isThrownBy(() -> service.personalResult(session.getId(), UUID.randomUUID()));
    }

    @Test
    void personalResultHoldsEvenTheirOwnScoreOnTheQuizsLastQuestion() {
        // QUESTION is the quiz's *last* question: LEADERBOARD is showing, but
        // the session is still IN_PROGRESS (the host hasn't advanced past it
        // yet). The hold predates this milestone and stays: the last
        // question's screen belongs to the ceremony, so the participant sees
        // the "watch the shared screen" state rather than a final tally.
        Session session = inProgressSession(hostUser);
        session.closeQuestion();
        session.revealAnswer();
        session.showLeaderboard();
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(gameplayQuizQuery.load(QUIZ_VERSION)).thenReturn(quizOfOneQuestion(QUESTION));

        assertThatExceptionOfType(ResultsNotAvailableException.class)
                .isThrownBy(() -> service.personalResult(session.getId(), UUID.randomUUID()));
    }

    @Test
    void personalResultStaysAvailableOnANonFinalQuestionsLeaderboard() {
        // Same LEADERBOARD phase, but QUESTION is not the quiz's last
        // question — the interim-standings read every non-final question
        // relies on must keep working exactly as before.
        Session session = inProgressSession(hostUser);
        session.closeQuestion();
        session.revealAnswer();
        session.showLeaderboard();
        Participant me = player("Me", 750, 750);
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(participantRepository.findBySessionId(session.getId())).thenReturn(List.of(me));
        when(gameplayQuizQuery.load(QUIZ_VERSION)).thenReturn(quizWithQuestions(2));

        ParticipantResultView view = service.personalResult(session.getId(), me.getId());

        assertThat(view.participantId()).isEqualTo(me.getId());
    }

    /**
     * A participant whose whole score came from the question in play (or
     * who never answered it, when {@code pointsOnCurrentQuestion} is null).
     */
    private static Participant player(String name, int totalScore, Integer pointsOnCurrentQuestion) {
        Participant participant = Participant.guest(UUID.randomUUID(),
                GuestParticipantToken.generate(), name, LanguageCode.of("en"));
        if (pointsOnCurrentQuestion != null) {
            participant.recordAnswer(new ParticipantAnswer(QUESTION, Set.of(UUID.randomUUID()),
                    LanguageCode.of("en"), NOW, 1000, pointsOnCurrentQuestion));
        }
        int remainder = totalScore - (pointsOnCurrentQuestion == null ? 0 : pointsOnCurrentQuestion);
        if (remainder > 0) {
            participant.recordAnswer(new ParticipantAnswer(UUID.randomUUID(), Set.of(UUID.randomUUID()),
                    LanguageCode.of("en"), NOW, 1000, remainder));
        }
        return participant;
    }

    private static Session inProgressSession(CurrentUser host) {
        Session session = sessionHostedBy(host, "424242");
        session.openLobby();
        session.registerParticipant(UUID.randomUUID(),
                ParticipantKey.forGuest(GuestParticipantToken.generate()));
        session.start();
        session.previewQuestion(QUESTION, QuestionTimer.startingAt(NOW, Duration.ofSeconds(5)));
        session.openQuestion(QuestionTimer.startingAt(NOW, Duration.ofSeconds(30)));
        return session;
    }

    /**
     * {@code count} questions, the first of which is always {@code QUESTION}
     * (the id every fixture session is currently playing) — so it is never
     * the quiz's last question as long as {@code count >= 2}, and the
     * {@code isLastQuestion} check in {@link SessionResultsQueryService}
     * correctly reads these as "a non-final question in play."
     */
    private static PlayableQuizView quizWithQuestions(int count) {
        List<PlayableQuizView.PlayableQuestion> questions = new java.util.ArrayList<>();
        questions.add(new PlayableQuizView.PlayableQuestion(
                QUESTION, Difficulty.EASY, Set.of(UUID.randomUUID()), Set.of(UUID.randomUUID())));
        for (int index = 1; index < count; index++) {
            questions.add(new PlayableQuizView.PlayableQuestion(
                    UUID.randomUUID(), Difficulty.EASY, Set.of(UUID.randomUUID()),
                    Set.of(UUID.randomUUID())));
        }
        return new PlayableQuizView(30, questions);
    }

    /** A one-question quiz whose sole question is the quiz's last by definition. */
    private static PlayableQuizView quizOfOneQuestion(UUID questionId) {
        return new PlayableQuizView(30, List.of(new PlayableQuizView.PlayableQuestion(
                questionId, Difficulty.EASY, Set.of(UUID.randomUUID()), Set.of(UUID.randomUUID()))));
    }
}
