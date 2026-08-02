package io.quizchef.session.application;

import static io.quizchef.session.application.SessionOrchestrationTestFixtures.QUIZ_VERSION;
import static io.quizchef.session.application.SessionOrchestrationTestFixtures.host;
import static io.quizchef.session.application.SessionOrchestrationTestFixtures.sessionHostedBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.quizchef.identity.application.AuthorizationService;
import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.quiz.application.GameplayQuizQuery;
import io.quizchef.quiz.application.PlayableQuizView;
import io.quizchef.quiz.domain.Difficulty;
import io.quizchef.quiz.domain.LanguageCode;
import io.quizchef.session.domain.GuestParticipantToken;
import io.quizchef.session.domain.LeaderboardService;
import io.quizchef.session.domain.Participant;
import io.quizchef.session.domain.ParticipantAnswer;
import io.quizchef.session.domain.QuestionTimer;
import io.quizchef.session.domain.Session;
import io.quizchef.session.domain.exception.NotSessionHostException;
import io.quizchef.session.domain.exception.TopFiveLeaderboardNotAvailableException;
import io.quizchef.session.infrastructure.persistence.ParticipantRepository;
import io.quizchef.session.infrastructure.persistence.SessionRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The host's animated Top 5 projection. Deliberately runs against the
 * <em>real</em> {@link LeaderboardService} rather than a mock: the whole
 * value of this endpoint is that both boards are that service's own
 * rankings, so a test that stubbed them would prove nothing about the
 * before-board actually being a ranking of the earlier state.
 */
class TopFiveLeaderboardQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-02T10:00:00Z");
    private static final LanguageCode EN = LanguageCode.of("en");

    private static final UUID EARLIER_QUESTION = UUID.randomUUID();
    private static final UUID CURRENT_QUESTION = UUID.randomUUID();
    private static final UUID LATER_QUESTION = UUID.randomUUID();

    private final SessionRepository sessionRepository = mock(SessionRepository.class);
    private final ParticipantRepository participantRepository = mock(ParticipantRepository.class);
    private final GameplayQuizQuery gameplayQuizQuery = mock(GameplayQuizQuery.class);
    private final AuthorizationService authorizationService = mock(AuthorizationService.class);
    private final TopFiveLeaderboardQueryService service = new TopFiveLeaderboardQueryService(
            sessionRepository, participantRepository, new LeaderboardService(), gameplayQuizQuery,
            authorizationService);

    private final CurrentUser hostUser = host();

    @Test
    void projectsFiveRowsPerBoardAndNeverASixth() {
        Scenario scenario = sixPlayersOneOfWhomStormsTheTopFive();

        TopFiveLeaderboardTransitionView view = service.transition(hostUser, scenario.sessionId());

        assertThat(view.currentTopFive()).hasSize(5);
        assertThat(view.previousTopFive()).hasSize(5);
        assertThat(view.currentTopFive())
                .extracting(TopFiveLeaderboardTransitionView.Entry::displayName,
                        TopFiveLeaderboardTransitionView.Entry::currentRank)
                .containsExactly(
                        tuple("Fran", 1), tuple("Ann", 2), tuple("Ben", 3),
                        tuple("Cara", 4), tuple("Dave", 5));
        // Sixth place — Erin, who just dropped out — is not on the current
        // board at all, and ranks 6 onward never appear anywhere.
        assertThat(view.currentTopFive())
                .extracting(TopFiveLeaderboardTransitionView.Entry::displayName)
                .doesNotContain("Erin");
        assertThat(view.currentTopFive())
                .extracting(TopFiveLeaderboardTransitionView.Entry::currentRank)
                .allMatch(rank -> rank <= 5);
        assertThat(view.questionId()).isEqualTo(CURRENT_QUESTION);
        assertThat(view.questionNumber()).isEqualTo(2);
        assertThat(view.totalQuestions()).isEqualTo(3);
        assertThat(view.finalQuestion()).isFalse();
    }

    @Test
    void carriesBothScoresAndTheQuestionsOwnPointsForEveryRow() {
        Scenario scenario = sixPlayersOneOfWhomStormsTheTopFive();

        TopFiveLeaderboardTransitionView view = service.transition(hostUser, scenario.sessionId());

        // The mover: 400 before this question, 1000 awarded by it, 1400 now
        // — every one of the three numbers is the server's, and they agree.
        TopFiveLeaderboardTransitionView.Entry fran = row(view.currentTopFive(), "Fran");
        assertThat(fran.previousScore()).isEqualTo(400);
        assertThat(fran.pointsEarned()).isEqualTo(1000);
        assertThat(fran.currentScore()).isEqualTo(1400);

        // Someone who answered nothing this question still carries an
        // honest 0, not a missing value the client has to interpret.
        TopFiveLeaderboardTransitionView.Entry ann = row(view.currentTopFive(), "Ann");
        assertThat(ann.pointsEarned()).isZero();
        assertThat(ann.previousScore()).isEqualTo(900);
        assertThat(ann.currentScore()).isEqualTo(900);

        assertThat(view.currentTopFive()).allSatisfy(entry ->
                assertThat(entry.previousScore() + entry.pointsEarned()).isEqualTo(entry.currentScore()));
    }

    @Test
    void ranksMovementAgainstTheBoardAsItActuallyStoodBefore() {
        Scenario scenario = sixPlayersOneOfWhomStormsTheTopFive();

        TopFiveLeaderboardTransitionView view = service.transition(hostUser, scenario.sessionId());

        // Ann led the previous board and is second now: the client can say
        // "down 1" because both ranks are the server's, not a diff of two
        // renders it happened to have seen.
        TopFiveLeaderboardTransitionView.Entry ann = row(view.currentTopFive(), "Ann");
        assertThat(ann.previousRank()).isEqualTo(1);
        assertThat(ann.currentRank()).isEqualTo(2);
    }

    @Test
    void withholdsTheRankAnEntrantAndALeaverNeverHeldOnTheVisibleBoard() {
        Scenario scenario = sixPlayersOneOfWhomStormsTheTopFive();

        TopFiveLeaderboardTransitionView view = service.transition(hostUser, scenario.sessionId());

        // Fran was sixth before — outside the projected board — so no
        // previous rank crosses the wire: the client shows "New Top 5"
        // instead of inventing a movement distance from a 6 it never saw.
        assertThat(row(view.currentTopFive(), "Fran").previousRank()).isNull();

        // Erin is now sixth. She is on the previous board (the animation
        // has to count her score up before she leaves), but her *new*
        // position below fifth is not disclosed.
        TopFiveLeaderboardTransitionView.Entry erin = row(view.previousTopFive(), "Erin");
        assertThat(erin.previousRank()).isEqualTo(5);
        assertThat(erin.currentRank()).isNull();
    }

    @Test
    void projectsOnlyTheParticipantsThatExistInASmallRoom() {
        Scenario scenario = scenario(List.of(
                player("Ann", Map.of(EARLIER_QUESTION, 900)),
                player("Ben", Map.of(EARLIER_QUESTION, 800, CURRENT_QUESTION, 50))));

        TopFiveLeaderboardTransitionView view = service.transition(hostUser, scenario.sessionId());

        assertThat(view.currentTopFive())
                .extracting(TopFiveLeaderboardTransitionView.Entry::displayName)
                .containsExactly("Ann", "Ben");
        assertThat(view.previousTopFive()).hasSize(2);
    }

    @Test
    void rendersEqualScoresAtTheDistinctRanksTheRankingServiceAssigned() {
        // Two identical scores are still two distinct ranks — the ranking
        // service breaks the tie (earliest submission) and this projection
        // reports whatever it decided, never collapsing them into a shared
        // placement of its own invention.
        Scenario scenario = scenario(List.of(
                player("Ann", Map.of(CURRENT_QUESTION, 900), NOW.plusSeconds(20)),
                player("Ben", Map.of(CURRENT_QUESTION, 900), NOW.plusSeconds(10))));

        TopFiveLeaderboardTransitionView view = service.transition(hostUser, scenario.sessionId());

        assertThat(view.currentTopFive())
                .extracting(TopFiveLeaderboardTransitionView.Entry::displayName,
                        TopFiveLeaderboardTransitionView.Entry::currentRank)
                .containsExactly(tuple("Ben", 1), tuple("Ann", 2));
        assertThat(view.currentTopFive())
                .extracting(TopFiveLeaderboardTransitionView.Entry::currentScore)
                .containsExactly(900, 900);
    }

    @Test
    void refusesBeforeTheAnswerIsRevealed() {
        Scenario scenario = sixPlayersOneOfWhomStormsTheTopFive();
        Session session = scenario.session();
        // Rewind to a fresh question: standings mid-question would leak who
        // answered correctly before the reveal (ADR-006).
        session.showLeaderboard();
        session.previewQuestion(LATER_QUESTION, QuestionTimer.startingAt(NOW, Duration.ofSeconds(5)));
        session.openQuestion(QuestionTimer.startingAt(NOW, Duration.ofSeconds(30)));

        assertThatExceptionOfType(TopFiveLeaderboardNotAvailableException.class)
                .isThrownBy(() -> service.transition(hostUser, scenario.sessionId()));

        session.closeQuestion();
        assertThatExceptionOfType(TopFiveLeaderboardNotAvailableException.class)
                .isThrownBy(() -> service.transition(hostUser, scenario.sessionId()));
    }

    @Test
    void refusesOnTheQuizsLastQuestion() {
        Scenario scenario = sixPlayersOneOfWhomStormsTheTopFive();
        Session session = scenario.session();
        session.showLeaderboard();
        session.previewQuestion(LATER_QUESTION, QuestionTimer.startingAt(NOW, Duration.ofSeconds(5)));
        session.openQuestion(QuestionTimer.startingAt(NOW, Duration.ofSeconds(30)));
        session.closeQuestion();
        session.revealAnswer();

        // Same phase that serves every other question, but this one is the
        // quiz's last: its standings are the final ones, and those belong
        // to the podium — there is no interim board to animate at all.
        assertThatExceptionOfType(TopFiveLeaderboardNotAvailableException.class)
                .isThrownBy(() -> service.transition(hostUser, scenario.sessionId()));
    }

    @Test
    void refusesAnyoneButTheSessionsOwnHost() {
        Scenario scenario = sixPlayersOneOfWhomStormsTheTopFive();

        // Authorized for QUIZ_HOST, but hosting someone else's session.
        assertThatExceptionOfType(NotSessionHostException.class)
                .isThrownBy(() -> service.transition(host(), scenario.sessionId()));
    }

    // --- fixtures ------------------------------------------------------------

    /**
     * Six players, ranked 900/800/700/600/500/400 going into the question
     * in play. Only Fran — sixth, and outside the projected board — scores
     * on it, taking the lead outright and pushing Erin out of the Top 5.
     * One scenario covers an entrant, a leaver, a demotion, and the sixth
     * row that must never be projected.
     */
    private Scenario sixPlayersOneOfWhomStormsTheTopFive() {
        return scenario(List.of(
                player("Ann", Map.of(EARLIER_QUESTION, 900)),
                player("Ben", Map.of(EARLIER_QUESTION, 800)),
                player("Cara", Map.of(EARLIER_QUESTION, 700)),
                player("Dave", Map.of(EARLIER_QUESTION, 600)),
                player("Erin", Map.of(EARLIER_QUESTION, 500)),
                player("Fran", Map.of(EARLIER_QUESTION, 400, CURRENT_QUESTION, 1000))));
    }

    /**
     * A session sitting on {@code CURRENT_QUESTION} with its answer
     * revealed — the exact moment the host's leaderboard animates.
     */
    private Scenario scenario(List<Participant> players) {
        Session session = sessionHostedBy(hostUser, "424242");
        session.openLobby();
        players.forEach(player -> session.registerParticipant(player.getId(), player.key()));
        session.start();
        session.previewQuestion(EARLIER_QUESTION, QuestionTimer.startingAt(NOW, Duration.ofSeconds(5)));
        session.openQuestion(QuestionTimer.startingAt(NOW, Duration.ofSeconds(30)));
        session.closeQuestion();
        session.revealAnswer();
        session.showLeaderboard();
        session.previewQuestion(CURRENT_QUESTION, QuestionTimer.startingAt(NOW, Duration.ofSeconds(5)));
        session.openQuestion(QuestionTimer.startingAt(NOW, Duration.ofSeconds(30)));
        session.closeQuestion();
        session.revealAnswer();

        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(participantRepository.findBySessionId(session.getId())).thenReturn(players);
        when(gameplayQuizQuery.load(QUIZ_VERSION)).thenReturn(quizOfThreeQuestions());
        return new Scenario(session, players);
    }

    private static Participant player(String name, Map<UUID, Integer> pointsByQuestion) {
        return player(name, pointsByQuestion, NOW.plusSeconds(10));
    }

    private static Participant player(String name, Map<UUID, Integer> pointsByQuestion, Instant answeredAt) {
        Participant participant = Participant.guest(
                UUID.randomUUID(), GuestParticipantToken.generate(), name, EN);
        // Ordered so the answer times are deterministic across questions.
        Map<UUID, Integer> ordered = new LinkedHashMap<>(pointsByQuestion);
        ordered.forEach((questionId, points) -> participant.recordAnswer(new ParticipantAnswer(
                questionId, Set.of(UUID.randomUUID()), EN, answeredAt, 1000, points)));
        return participant;
    }

    private static PlayableQuizView quizOfThreeQuestions() {
        List<PlayableQuizView.PlayableQuestion> questions = new ArrayList<>();
        for (UUID questionId : List.of(EARLIER_QUESTION, CURRENT_QUESTION, LATER_QUESTION)) {
            questions.add(new PlayableQuizView.PlayableQuestion(
                    questionId, Difficulty.EASY, Set.of(UUID.randomUUID()), Set.of(UUID.randomUUID())));
        }
        return new PlayableQuizView(30, questions);
    }

    private static TopFiveLeaderboardTransitionView.Entry row(
            List<TopFiveLeaderboardTransitionView.Entry> board, String displayName) {
        return board.stream()
                .filter(entry -> entry.displayName().equals(displayName))
                .findFirst()
                .orElseThrow(() -> new AssertionError(displayName + " is not on this board"));
    }

    private record Scenario(Session session, List<Participant> players) {

        UUID sessionId() {
            return session.getId();
        }
    }
}
