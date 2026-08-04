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
import io.quizchef.session.domain.FinalPlacementLabel;
import io.quizchef.session.domain.FinalPlacementVisibility;
import io.quizchef.session.domain.GuestParticipantToken;
import io.quizchef.session.domain.LeaderboardService;
import io.quizchef.session.domain.Participant;
import io.quizchef.session.domain.ParticipantAnswer;
import io.quizchef.session.domain.QuestionTimer;
import io.quizchef.session.domain.Session;
import io.quizchef.session.domain.exception.ParticipantNotFoundException;
import io.quizchef.session.domain.exception.ResultsNotAvailableException;
import io.quizchef.session.infrastructure.persistence.ParticipantRepository;
import io.quizchef.session.infrastructure.persistence.SessionRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * A participant's own finish. Runs against the real
 * {@link LeaderboardService} and the real policy — the whole question here
 * is which side of an authoritative cutoff a real ranking puts someone on,
 * and stubbing either would answer it in the test instead of the code.
 */
class ParticipantFinalPlacementQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-04T10:00:00Z");
    private static final LanguageCode EN = LanguageCode.of("en");
    private static final UUID QUESTION = UUID.randomUUID();

    private final SessionRepository sessionRepository = mock(SessionRepository.class);
    private final ParticipantRepository participantRepository = mock(ParticipantRepository.class);
    private final GameplayQuizQuery gameplayQuizQuery = mock(GameplayQuizQuery.class);
    private final ParticipantFinalPlacementQueryService service =
            new ParticipantFinalPlacementQueryService(sessionRepository, participantRepository,
                    new LeaderboardService(), gameplayQuizQuery);

    private final CurrentUser hostUser = host();

    @Test
    void tellsTheWinnerExactlyWhereTheyCame() {
        List<Participant> players = players(10);
        UUID sessionId = releasedSession(players);

        ParticipantFinalPlacementView view = service.placement(sessionId, players.get(0).getId());

        assertThat(view.visibility()).isEqualTo(FinalPlacementVisibility.EXACT_RANK);
        assertThat(view.rank()).isEqualTo(1);
        assertThat(view.label()).isEqualTo(FinalPlacementLabel.WINNER);
        assertThat(view.score()).isEqualTo(1000);
        assertThat(view.participantCount()).isEqualTo(10);
    }

    @Test
    void labelsFourthAndFifthRunnersUpAndTheRestOfTheGroupFinalists() {
        // 20 players → the reveal group runs to rank 10.
        List<Participant> players = players(20);
        UUID sessionId = releasedSession(players);

        assertThat(service.placement(sessionId, players.get(3).getId()).label())
                .isEqualTo(FinalPlacementLabel.RUNNER_UP);
        assertThat(service.placement(sessionId, players.get(4).getId()).label())
                .isEqualTo(FinalPlacementLabel.RUNNER_UP);
        ParticipantFinalPlacementView sixth = service.placement(sessionId, players.get(5).getId());
        assertThat(sixth.label()).isEqualTo(FinalPlacementLabel.FINALIST);
        assertThat(sixth.rank()).isEqualTo(6);
        ParticipantFinalPlacementView tenth = service.placement(sessionId, players.get(9).getId());
        assertThat(tenth.visibility()).isEqualTo(FinalPlacementVisibility.EXACT_RANK);
        assertThat(tenth.rank()).isEqualTo(10);
    }

    @Test
    void givesTheLowerHalfTheirScoreAndTheirNeighboursButNeverAPosition() {
        List<Participant> players = players(20);
        UUID sessionId = releasedSession(players);

        // 11th of 20 — the first player past the cutoff.
        ParticipantFinalPlacementView view = service.placement(sessionId, players.get(10).getId());

        assertThat(view.visibility()).isEqualTo(FinalPlacementVisibility.RELATIVE_ONLY);
        assertThat(view.rank()).isNull();
        assertThat(view.label()).isNull();
        assertThat(view.score()).isEqualTo(1000 - 10);
        // They finished behind the player above and ahead of the one below —
        // by name, and by name alone.
        assertThat(view.behind().displayName()).isEqualTo("P10");
        assertThat(view.aheadOf().displayName()).isEqualTo("P12");
        assertThat(view.tiedWith()).isNull();
    }

    @Test
    void tellsTheLastPlayerOnlyWhoTheyFinishedBehind() {
        List<Participant> players = players(20);
        UUID sessionId = releasedSession(players);

        ParticipantFinalPlacementView view = service.placement(sessionId, players.get(19).getId());

        assertThat(view.visibility()).isEqualTo(FinalPlacementVisibility.RELATIVE_ONLY);
        assertThat(view.behind().displayName()).isEqualTo("P19");
        // Nobody finished below them, and the response says nothing rather
        // than saying so.
        assertThat(view.aheadOf()).isNull();
    }

    @Test
    void carriesNoNeighbourRankScoreOrGapAnywhereInTheShape() {
        // The strongest form of this guarantee is structural: the neighbour
        // type has exactly one component, so there is no field for a rank,
        // a score, or a difference to travel in.
        assertThat(ParticipantFinalPlacementView.Neighbour.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly("displayName");
    }

    @Test
    void givesTheOnlyPlayerFirstPlace() {
        List<Participant> players = players(1);
        UUID sessionId = releasedSession(players);

        ParticipantFinalPlacementView view = service.placement(sessionId, players.get(0).getId());

        assertThat(view.visibility()).isEqualTo(FinalPlacementVisibility.EXACT_RANK);
        assertThat(view.rank()).isEqualTo(1);
        assertThat(view.aheadOf()).isNull();
        assertThat(view.behind()).isNull();
    }

    @Test
    void revealsEveryoneInARoomOfFour() {
        List<Participant> players = players(4);
        UUID sessionId = releasedSession(players);

        // min(5,4)=4 beats ceil(4/2)=2 — nobody is left in the relative-only
        // group in a room this small.
        assertThat(players).allSatisfy(player ->
                assertThat(service.placement(sessionId, player.getId()).visibility())
                        .isEqualTo(FinalPlacementVisibility.EXACT_RANK));
    }

    @Test
    void holdsEverythingUntilTheHostReleasesResults() {
        List<Participant> players = players(10);
        Session session = finishedSession(players);
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(participantRepository.findBySessionId(session.getId())).thenReturn(players);

        // FINISHED, but the ceremony has not run: no placement of any kind,
        // exact or relative, for anyone.
        assertThatExceptionOfType(ResultsNotAvailableException.class)
                .isThrownBy(() -> service.placement(session.getId(), players.get(0).getId()));
        assertThatExceptionOfType(ResultsNotAvailableException.class)
                .isThrownBy(() -> service.placement(session.getId(), players.get(9).getId()));
    }

    @Test
    void refusesWhileTheQuizIsStillRunning() {
        List<Participant> players = players(4);
        Session session = sessionWith(players);
        session.start();
        session.previewQuestion(QUESTION, QuestionTimer.startingAt(NOW, Duration.ofSeconds(5)));
        session.openQuestion(QuestionTimer.startingAt(NOW, Duration.ofSeconds(30)));
        session.closeQuestion();
        session.revealAnswer();
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(participantRepository.findBySessionId(session.getId())).thenReturn(players);

        assertThatExceptionOfType(ResultsNotAvailableException.class)
                .isThrownBy(() -> service.placement(session.getId(), players.get(0).getId()));
    }

    @Test
    void refusesAGuessedParticipantId() {
        List<Participant> players = players(4);
        UUID sessionId = releasedSession(players);

        assertThatExceptionOfType(ParticipantNotFoundException.class)
                .isThrownBy(() -> service.placement(sessionId, UUID.randomUUID()));
    }

    // --- fixtures ------------------------------------------------------------

    /** {@code count} players scoring 1000, 999, … so ranks are unambiguous. */
    private static List<Participant> players(int count) {
        List<Participant> players = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            Participant participant = Participant.guest(UUID.randomUUID(),
                    GuestParticipantToken.generate(), "P" + (index + 1), EN);
            participant.recordAnswer(new ParticipantAnswer(QUESTION, Set.of(UUID.randomUUID()),
                    EN, NOW, 1000, 1000 - index));
            players.add(participant);
        }
        return players;
    }

    private UUID releasedSession(List<Participant> players) {
        Session session = finishedSession(players);
        session.releaseFinalResults();
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(participantRepository.findBySessionId(session.getId())).thenReturn(players);
        return session.getId();
    }

    private Session finishedSession(List<Participant> players) {
        Session session = sessionWith(players);
        session.start();
        session.previewQuestion(QUESTION, QuestionTimer.startingAt(NOW, Duration.ofSeconds(5)));
        session.openQuestion(QuestionTimer.startingAt(NOW, Duration.ofSeconds(30)));
        session.closeQuestion();
        session.revealAnswer();
        session.finish();
        when(gameplayQuizQuery.load(QUIZ_VERSION)).thenReturn(quizOfOneQuestion());
        return session;
    }

    private Session sessionWith(List<Participant> players) {
        Session session = sessionHostedBy(hostUser, "424242");
        session.openLobby();
        players.forEach(player -> session.registerParticipant(player.getId(), player.key()));
        when(gameplayQuizQuery.load(QUIZ_VERSION)).thenReturn(quizOfOneQuestion());
        return session;
    }

    private static PlayableQuizView quizOfOneQuestion() {
        return new PlayableQuizView(30, List.of(new PlayableQuizView.PlayableQuestion(
                QUESTION, Difficulty.EASY, Set.of(UUID.randomUUID()), Set.of(UUID.randomUUID()))));
    }
}
