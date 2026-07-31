package io.quizchef.session.application;

import static io.quizchef.session.application.SessionOrchestrationTestFixtures.QUIZ_VERSION;
import static io.quizchef.session.application.SessionOrchestrationTestFixtures.host;
import static io.quizchef.session.application.SessionOrchestrationTestFixtures.sessionHostedBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.quiz.application.GameplayQuizQuery;
import io.quizchef.quiz.application.PlayableQuizView;
import io.quizchef.quiz.domain.Difficulty;
import io.quizchef.quiz.domain.LanguageCode;
import io.quizchef.session.domain.GuestParticipantToken;
import io.quizchef.session.domain.LeaderboardService;
import io.quizchef.session.domain.Participant;
import io.quizchef.session.domain.ParticipantAnswer;
import io.quizchef.session.domain.ParticipantKey;
import io.quizchef.session.domain.QuestionTimer;
import io.quizchef.session.domain.Session;
import io.quizchef.session.domain.exception.ParticipantNotFoundException;
import io.quizchef.session.domain.exception.RankContextNotAvailableException;
import io.quizchef.session.infrastructure.persistence.ParticipantRepository;
import io.quizchef.session.infrastructure.persistence.SessionRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ParticipantRankContextQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-31T10:00:00Z");
    private static final UUID PREVIOUS_QUESTION = UUID.randomUUID();

    private final SessionRepository sessionRepository = mock(SessionRepository.class);
    private final ParticipantRepository participantRepository = mock(ParticipantRepository.class);
    private final LeaderboardService leaderboardService = new LeaderboardService();
    private final GameplayQuizQuery gameplayQuizQuery = mock(GameplayQuizQuery.class);
    private final ParticipantRankContextQueryService service = new ParticipantRankContextQueryService(
            sessionRepository, participantRepository, leaderboardService, gameplayQuizQuery);

    private final CurrentUser hostUser = host();
    private final UUID questionId = UUID.randomUUID();

    /** A two-question quiz currently on {@code questionId}, with one more question after it. */
    private Session nonFinalQuestionSession() {
        UUID laterQuestion = UUID.randomUUID();
        return sessionAt(List.of(questionId, laterQuestion), questionId);
    }

    /** A two-question quiz whose last question — currently in play — is {@code questionId}. */
    private Session finalQuestionSession() {
        UUID earlierQuestion = UUID.randomUUID();
        return sessionAt(List.of(earlierQuestion, questionId), questionId);
    }

    private Session sessionAt(List<UUID> orderedQuestionIds, UUID currentQuestionId) {
        Session session = sessionHostedBy(hostUser, "700001");
        session.openLobby();
        session.registerParticipant(UUID.randomUUID(),
                ParticipantKey.forGuest(GuestParticipantToken.generate()));
        session.start();
        session.openQuestion(currentQuestionId, QuestionTimer.startingAt(NOW, Duration.ofSeconds(30)));
        session.closeQuestion();
        session.revealAnswer();
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        List<PlayableQuizView.PlayableQuestion> questions = orderedQuestionIds.stream()
                .map(id -> new PlayableQuizView.PlayableQuestion(
                        id, Difficulty.EASY, Set.of(UUID.randomUUID()), Set.of(UUID.randomUUID())))
                .toList();
        when(gameplayQuizQuery.load(QUIZ_VERSION)).thenReturn(new PlayableQuizView(30, questions));
        return session;
    }

    private Participant participantScoring(Session session, String name, int currentQuestionPoints,
                                           int earlierPoints, Instant submittedAt) {
        Participant participant = Participant.guest(session.getId(),
                GuestParticipantToken.generate(), name, LanguageCode.of("en"));
        session.registerParticipant(participant.getId(), participant.key());
        participant.connect(NOW);
        if (earlierPoints > 0) {
            participant.recordAnswer(new ParticipantAnswer(
                    PREVIOUS_QUESTION, Set.of(UUID.randomUUID()), LanguageCode.of("en"),
                    NOW.minusSeconds(60), 1_000L, earlierPoints));
        }
        participant.recordAnswer(new ParticipantAnswer(
                questionId, Set.of(UUID.randomUUID()), LanguageCode.of("en"),
                submittedAt, 1_000L, currentQuestionPoints));
        return participant;
    }

    @Test
    void middleRankedParticipantSeesOneAheadAndOneBehind() {
        Session session = nonFinalQuestionSession();
        Participant ahead = participantScoring(session, "Ahead", 700, 0, NOW);
        Participant own = participantScoring(session, "Own", 150, 500, NOW);
        Participant behind = participantScoring(session, "Behind", 50, 0, NOW);
        when(participantRepository.findBySessionId(session.getId()))
                .thenReturn(List.of(ahead, own, behind));

        ParticipantRankContextView view = service.rankContext(session.getId(), own.getId());

        assertThat(view.rank()).isEqualTo(2);
        assertThat(view.score()).isEqualTo(650);
        assertThat(view.pointsEarned()).isEqualTo(150);
        assertThat(view.ahead()).isNotNull();
        assertThat(view.ahead().displayName()).isEqualTo("Ahead");
        assertThat(view.ahead().scoreDifference()).isEqualTo(50);
        assertThat(view.behind()).isNotNull();
        assertThat(view.behind().displayName()).isEqualTo("Behind");
        assertThat(view.behind().scoreDifference()).isEqualTo(600);
        assertThat(view.tiedWith()).isNull();
    }

    @Test
    void firstPlaceSeesOnlyBehind() {
        Session session = nonFinalQuestionSession();
        Participant own = participantScoring(session, "Own", 900, 0, NOW);
        Participant behind = participantScoring(session, "Behind", 100, 0, NOW);
        when(participantRepository.findBySessionId(session.getId())).thenReturn(List.of(own, behind));

        ParticipantRankContextView view = service.rankContext(session.getId(), own.getId());

        assertThat(view.rank()).isEqualTo(1);
        assertThat(view.ahead()).isNull();
        assertThat(view.behind()).isNotNull();
        assertThat(view.behind().displayName()).isEqualTo("Behind");
    }

    @Test
    void lastPlaceSeesOnlyAhead() {
        Session session = nonFinalQuestionSession();
        Participant ahead = participantScoring(session, "Ahead", 900, 0, NOW);
        Participant own = participantScoring(session, "Own", 100, 0, NOW);
        when(participantRepository.findBySessionId(session.getId())).thenReturn(List.of(ahead, own));

        ParticipantRankContextView view = service.rankContext(session.getId(), own.getId());

        assertThat(view.rank()).isEqualTo(2);
        assertThat(view.behind()).isNull();
        assertThat(view.ahead()).isNotNull();
        assertThat(view.ahead().displayName()).isEqualTo("Ahead");
    }

    @Test
    void aSoloParticipantSeesNoNeighbours() {
        Session session = nonFinalQuestionSession();
        Participant own = participantScoring(session, "Own", 100, 0, NOW);
        when(participantRepository.findBySessionId(session.getId())).thenReturn(List.of(own));

        ParticipantRankContextView view = service.rankContext(session.getId(), own.getId());

        assertThat(view.ahead()).isNull();
        assertThat(view.behind()).isNull();
        assertThat(view.tiedWith()).isNull();
    }

    @Test
    void anEqualScoreNeighbourIsReportedAsTiedRatherThanAheadOrBehind() {
        Session session = nonFinalQuestionSession();
        // Own registers first, so ties break in own's favour (earlier join order).
        Participant own = participantScoring(session, "Own", 300, 0, NOW);
        Participant tiedPeer = participantScoring(session, "Peer", 300, 0, NOW);
        Participant other = participantScoring(session, "Other", 100, 0, NOW);
        when(participantRepository.findBySessionId(session.getId()))
                .thenReturn(List.of(own, tiedPeer, other));

        ParticipantRankContextView view = service.rankContext(session.getId(), own.getId());

        assertThat(view.ahead()).isNull();
        assertThat(view.behind()).isNull();
        assertThat(view.tiedWith()).isNotNull();
        assertThat(view.tiedWith().displayName()).isEqualTo("Peer");
    }

    @Test
    void unavailableForTheQuizsFinalQuestion() {
        Session session = finalQuestionSession();
        Participant own = participantScoring(session, "Own", 100, 0, NOW);
        when(participantRepository.findBySessionId(session.getId())).thenReturn(List.of(own));

        assertThatExceptionOfType(RankContextNotAvailableException.class)
                .isThrownBy(() -> service.rankContext(session.getId(), own.getId()));
    }

    @Test
    void unavailableBeforeTheAnswerIsRevealed() {
        Session session = sessionHostedBy(hostUser, "700099");
        session.openLobby();
        UUID participantId = UUID.randomUUID();
        session.registerParticipant(participantId,
                ParticipantKey.forGuest(GuestParticipantToken.generate()));
        session.start();
        session.openQuestion(questionId, QuestionTimer.startingAt(NOW, Duration.ofSeconds(30)));
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));

        assertThatExceptionOfType(RankContextNotAvailableException.class)
                .isThrownBy(() -> service.rankContext(session.getId(), participantId));
    }

    @Test
    void anUnknownParticipantIsNotFound() {
        Session session = nonFinalQuestionSession();
        Participant own = participantScoring(session, "Own", 100, 0, NOW);
        when(participantRepository.findBySessionId(session.getId())).thenReturn(List.of(own));

        assertThatExceptionOfType(ParticipantNotFoundException.class)
                .isThrownBy(() -> service.rankContext(session.getId(), UUID.randomUUID()));
    }
}
