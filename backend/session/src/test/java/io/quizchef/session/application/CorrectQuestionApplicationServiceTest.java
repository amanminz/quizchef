package io.quizchef.session.application;

import static io.quizchef.session.application.SessionOrchestrationTestFixtures.host;
import static io.quizchef.session.application.SessionOrchestrationTestFixtures.sessionHostedBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.quizchef.common.event.DomainEvent;
import io.quizchef.common.event.DomainEventPublisher;
import io.quizchef.identity.application.AuthorizationService;
import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.identity.domain.Permission;
import io.quizchef.quiz.application.GameplayQuestionContentQuery;
import io.quizchef.quiz.application.GameplayQuizQuery;
import io.quizchef.quiz.application.PlayableQuizView;
import io.quizchef.quiz.application.PlayableQuizView.PlayableQuestion;
import io.quizchef.quiz.domain.Difficulty;
import io.quizchef.quiz.domain.LanguageCode;
import io.quizchef.session.application.CorrectQuestionCommand.CorrectedLocalization;
import io.quizchef.session.application.CorrectQuestionCommand.CorrectedOption;
import io.quizchef.session.domain.GuestParticipantToken;
import io.quizchef.session.domain.Participant;
import io.quizchef.session.domain.ParticipantAnswer;
import io.quizchef.session.domain.ParticipantKey;
import io.quizchef.session.domain.QuestionTimer;
import io.quizchef.session.domain.Session;
import io.quizchef.session.domain.SessionPhase;
import io.quizchef.session.domain.SessionQuestionCorrection;
import io.quizchef.session.domain.event.QuestionCorrectedEvent;
import io.quizchef.session.domain.event.QuestionPreviewStartedEvent;
import io.quizchef.session.domain.exception.InvalidSessionTransitionException;
import io.quizchef.session.domain.exception.NotSessionHostException;
import io.quizchef.session.infrastructure.GameplayProperties;
import io.quizchef.session.infrastructure.persistence.ParticipantRepository;
import io.quizchef.session.infrastructure.persistence.SessionQuestionCorrectionRepository;
import io.quizchef.session.infrastructure.persistence.SessionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Fixing a question mid-session: what is saved, and when the fix also
 * replays the question.
 */
class CorrectQuestionApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-15T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final LanguageCode EN = LanguageCode.of("en");

    private static final UUID Q1 = UUID.randomUUID();
    private static final UUID Q2 = UUID.randomUUID();
    private static final UUID Q3 = UUID.randomUUID();
    private static final UUID OPTION_A = UUID.randomUUID();
    private static final UUID OPTION_B = UUID.randomUUID();

    private final SessionRepository sessionRepository = mock(SessionRepository.class);
    private final SessionQuestionCorrectionRepository correctionRepository =
            mock(SessionQuestionCorrectionRepository.class);
    private final ParticipantRepository participantRepository = mock(ParticipantRepository.class);
    private final GameplayQuizQuery gameplayQuizQuery = mock(GameplayQuizQuery.class);
    private final AuthorizationService authorizationService = mock(AuthorizationService.class);
    private final DomainEventPublisher eventPublisher = mock(DomainEventPublisher.class);
    private final QuestionTimerScheduler timerScheduler = mock(QuestionTimerScheduler.class);

    private final SessionQuizQuery sessionQuizQuery = new SessionQuizQuery(gameplayQuizQuery,
            mock(GameplayQuestionContentQuery.class), correctionRepository);
    private final CorrectQuestionApplicationService service = new CorrectQuestionApplicationService(
            sessionRepository, correctionRepository, sessionQuizQuery, authorizationService,
            new CancelQuestionAttempt(participantRepository),
            new QuestionOpener(eventPublisher, timerScheduler, new GameplayProperties(5), CLOCK),
            eventPublisher, CLOCK);

    private final CurrentUser hostUser = host();

    @Test
    void correctingAnUpcomingQuestionSavesTheFixAndLeavesPlayAlone() {
        Session session = sessionOnQuestion(Q1);
        stubQuiz();

        service.correct(hostUser, command(Q3, Set.of(OPTION_B)));

        verify(authorizationService).authorize(hostUser, Permission.QUIZ_HOST);
        assertThat(savedCorrection().getQuestionId()).isEqualTo(Q3);
        // Nothing is replayed and nothing is cancelled — the room has not
        // seen this question yet, so there is nothing to undo.
        assertThat(session.getCurrentQuestionId()).isEqualTo(Q1);
        assertThat(session.getCurrentPhase()).isEqualTo(SessionPhase.QUESTION_OPEN);
        verifyNoInteractions(participantRepository);
        verify(eventPublisher, never()).publish(any(QuestionPreviewStartedEvent.class));
        assertThat(correctedEvent().replayed()).isFalse();
    }

    @Test
    void correctingTheQuestionInPlayCancelsItsAnswersAndReplaysItFromTheReadingPeriod() {
        Session session = sessionOnQuestion(Q1);
        stubQuiz();
        Participant answered = connectedParticipant();
        answered.recordAnswer(new ParticipantAnswer(Q1, Set.of(OPTION_A), EN, NOW, 700, 900));
        stubParticipants(answered);

        service.correct(hostUser, command(Q1, Set.of(OPTION_B)));

        // Every effect of the attempt is gone...
        assertThat(answered.answers()).isEmpty();
        assertThat(answered.getTotalScore()).isZero();
        // ...and the same question starts over with a full reading period.
        assertThat(session.getCurrentQuestionId()).isEqualTo(Q1);
        assertThat(session.getCurrentPhase()).isEqualTo(SessionPhase.QUESTION_PREVIEW);
        assertThat(session.getCurrentQuestionTimer().durationSeconds()).isEqualTo(5);
        verify(timerScheduler).schedulePreviewEnd(eq(session.getId()), eq(Q1), any(), eq(30));
        assertThat(correctedEvent().replayed()).isTrue();
    }

    @Test
    void theReplayedQuestionIsScoredAgainstTheCorrectedAnswerKey() {
        Session session = sessionOnQuestion(Q1);
        stubQuiz();
        stubParticipants(connectedParticipant());
        ArgumentCaptor<SessionQuestionCorrection> saved =
                ArgumentCaptor.forClass(SessionQuestionCorrection.class);

        service.correct(hostUser, command(Q1, Set.of(OPTION_B)));

        verify(correctionRepository).saveAndFlush(saved.capture());
        when(correctionRepository.findBySessionId(session.getId()))
                .thenReturn(List.of(saved.getValue()));

        // The engine's scoring boundary now reports the corrected key, so
        // an answer given to the replayed question is judged by the fix.
        assertThat(sessionQuizQuery.effectiveQuiz(session).questions())
                .filteredOn(question -> question.questionId().equals(Q1))
                .singleElement()
                .satisfies(question ->
                        assertThat(question.correctOptionIds()).containsExactly(OPTION_B));
    }

    @Test
    void correctingTheSameQuestionAgainReplacesTheFixAndCountsARevision() {
        Session session = sessionOnQuestion(Q1);
        stubQuiz();
        stubParticipants(connectedParticipant());
        service.correct(hostUser, command(Q1, Set.of(OPTION_B)));
        SessionQuestionCorrection existing = savedCorrection();
        when(correctionRepository.findBySessionIdAndQuestionId(session.getId(), Q1))
                .thenReturn(Optional.of(existing));

        service.correct(hostUser, command(Q1, Set.of(OPTION_A)));

        assertThat(existing.getRevision()).isEqualTo(2);
        assertThat(existing.correctOptionIds()).containsExactly(OPTION_A);
    }

    @Test
    void refusesToCorrectAQuestionTheRoomHasAlreadyPlayed() {
        Session session = sessionOnQuestion(Q2);
        stubQuiz();

        // Q1 is behind the question in play: its answers are scored and its
        // standings shown, so rescoring it would change a leaderboard the
        // room has already seen.
        assertThatExceptionOfType(InvalidSessionTransitionException.class)
                .isThrownBy(() -> service.correct(hostUser, command(Q1, Set.of(OPTION_B))));
        assertThat(session.getCurrentPhase()).isEqualTo(SessionPhase.QUESTION_OPEN);
        verify(correctionRepository, never()).saveAndFlush(any());
    }

    @Test
    void refusesToCorrectAQuestionAlreadyRemovedFromTheSession() {
        Session session = sessionOnQuestion(Q1);
        stubQuiz();
        session.removeQuestion(Q3, Set.of(Q1, Q2, Q3), null, 0, NOW);

        assertThatExceptionOfType(InvalidSessionTransitionException.class)
                .isThrownBy(() -> service.correct(hostUser, command(Q3, Set.of(OPTION_B))));
    }

    @Test
    void refusesAnAnswerKeyPointingAtAnOptionTheQuestionDoesNotHave() {
        Session session = sessionOnQuestion(Q1);
        stubQuiz();
        stubParticipants(connectedParticipant());

        assertThatIllegalArgumentException().isThrownBy(() ->
                service.correct(hostUser, command(Q1, Set.of(UUID.randomUUID()))));
        // Validation fails before anything is undone: the room's answers are
        // still there, and the question is still running.
        assertThat(session.getCurrentPhase()).isEqualTo(SessionPhase.QUESTION_OPEN);
        verifyNoInteractions(participantRepository);
    }

    @Test
    void onlyTheHostingIdentityMayCorrectAQuestion() {
        Session session = sessionOnQuestion(Q1);

        assertThatExceptionOfType(NotSessionHostException.class)
                .isThrownBy(() -> service.correct(host(), command(Q1, Set.of(OPTION_B))));
        verify(correctionRepository, never()).saveAndFlush(any());
        assertThat(session.getCurrentPhase()).isEqualTo(SessionPhase.QUESTION_OPEN);
    }

    // --- fixtures ---

    private CorrectQuestionCommand command(UUID questionId, Set<UUID> correctOptionIds) {
        return new CorrectQuestionCommand(sessionId, questionId, correctOptionIds,
                List.of(new CorrectedLocalization("en", "The corrected prompt",
                        List.of(new CorrectedOption(OPTION_A, "Corrected A"),
                                new CorrectedOption(OPTION_B, "Corrected B")))));
    }

    private UUID sessionId;

    private Session sessionOnQuestion(UUID questionId) {
        Session session = sessionHostedBy(hostUser, "800001");
        session.openLobby();
        session.registerParticipant(UUID.randomUUID(),
                ParticipantKey.forGuest(GuestParticipantToken.generate()));
        session.start();
        session.previewQuestion(questionId, QuestionTimer.startingAt(NOW, Duration.ofSeconds(5)));
        session.openQuestion(QuestionTimer.startingAt(NOW, Duration.ofSeconds(30)));
        when(sessionRepository.findAndLockById(session.getId())).thenReturn(Optional.of(session));
        this.sessionId = session.getId();
        return session;
    }

    private void stubQuiz() {
        when(gameplayQuizQuery.load(any())).thenReturn(new PlayableQuizView(30,
                List.of(playable(Q1), playable(Q2), playable(Q3))));
    }

    private static PlayableQuestion playable(UUID questionId) {
        return new PlayableQuestion(questionId, Difficulty.EASY,
                Set.of(OPTION_A), Set.of(OPTION_A, OPTION_B));
    }

    private Participant connectedParticipant() {
        Participant participant = Participant.guest(UUID.randomUUID(),
                GuestParticipantToken.generate(), "Guest", EN);
        participant.connect(NOW);
        return participant;
    }

    private void stubParticipants(Participant... participants) {
        when(participantRepository.findBySessionId(any()))
                .thenReturn(new ArrayList<>(List.of(participants)));
    }

    private SessionQuestionCorrection savedCorrection() {
        ArgumentCaptor<SessionQuestionCorrection> captor =
                ArgumentCaptor.forClass(SessionQuestionCorrection.class);
        verify(correctionRepository, atLeastOnce()).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    /**
     * A replaying correction publishes a preview alongside the correction
     * itself, so events are captured as the port declares them and picked
     * out by type rather than by invocation count.
     */
    private QuestionCorrectedEvent correctedEvent() {
        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher, atLeastOnce()).publish(captor.capture());
        return captor.getAllValues().stream()
                .filter(QuestionCorrectedEvent.class::isInstance)
                .map(QuestionCorrectedEvent.class::cast)
                .reduce((first, second) -> second)
                .orElseThrow(() -> new AssertionError("no QuestionCorrectedEvent was published"));
    }
}
