package io.quizchef.session.application;

import static io.quizchef.session.application.SessionOrchestrationTestFixtures.host;
import static io.quizchef.session.application.SessionOrchestrationTestFixtures.sessionHostedBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
import io.quizchef.session.domain.FinalStanding;
import io.quizchef.session.domain.GuestParticipantToken;
import io.quizchef.session.domain.LeaderboardService;
import io.quizchef.session.domain.Participant;
import io.quizchef.session.domain.ParticipantAnswer;
import io.quizchef.session.domain.ParticipantKey;
import io.quizchef.session.domain.QuestionTimer;
import io.quizchef.session.domain.Session;
import io.quizchef.session.domain.SessionPhase;
import io.quizchef.session.domain.SessionState;
import io.quizchef.session.domain.event.QuestionPreviewStartedEvent;
import io.quizchef.session.domain.event.QuestionRemovedEvent;
import io.quizchef.session.domain.event.SessionFinishedEvent;
import io.quizchef.session.domain.exception.NotSessionHostException;
import io.quizchef.session.domain.exception.QuestionRemovalNotAllowedException;
import io.quizchef.session.infrastructure.GameplayProperties;
import io.quizchef.session.infrastructure.persistence.FinalStandingRepository;
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
 * Pulling a question out of a live session: what stops, what is reversed,
 * and what happens next.
 */
class RemoveQuestionApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-15T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final LanguageCode EN = LanguageCode.of("en");

    private static final UUID Q1 = UUID.randomUUID();
    private static final UUID Q2 = UUID.randomUUID();
    private static final UUID Q3 = UUID.randomUUID();
    private static final UUID OPTION = UUID.randomUUID();

    private final SessionRepository sessionRepository = mock(SessionRepository.class);
    private final ParticipantRepository participantRepository = mock(ParticipantRepository.class);
    private final FinalStandingRepository finalStandingRepository = mock(FinalStandingRepository.class);
    private final GameplayQuizQuery gameplayQuizQuery = mock(GameplayQuizQuery.class);
    private final AuthorizationService authorizationService = mock(AuthorizationService.class);
    private final DomainEventPublisher eventPublisher = mock(DomainEventPublisher.class);
    private final QuestionTimerScheduler timerScheduler = mock(QuestionTimerScheduler.class);

    private final SessionQuizQuery sessionQuizQuery = new SessionQuizQuery(gameplayQuizQuery,
            mock(GameplayQuestionContentQuery.class), mock(SessionQuestionCorrectionRepository.class));
    private final QuestionOpener questionOpener = new QuestionOpener(eventPublisher, timerScheduler,
            new GameplayProperties(5), CLOCK);
    private final RemoveQuestionApplicationService service = new RemoveQuestionApplicationService(
            sessionRepository, sessionQuizQuery, authorizationService,
            new CancelQuestionAttempt(participantRepository), questionOpener,
            new SessionFinisher(participantRepository, finalStandingRepository,
                    new LeaderboardService(), eventPublisher, CLOCK),
            eventPublisher, CLOCK);

    private final CurrentUser hostUser = host();

    @Test
    void removingAnUpcomingQuestionShortensTheQuizAndLeavesPlayAlone() {
        Session session = sessionOnQuestion(Q1);
        stubQuiz();

        service.remove(hostUser, session.getId(), Q3);

        verify(authorizationService).authorize(hostUser, Permission.QUIZ_HOST);
        assertThat(session.isRemoved(Q3)).isTrue();
        // The question in play is untouched — no cancel, no re-preview.
        assertThat(session.getCurrentQuestionId()).isEqualTo(Q1);
        assertThat(session.getCurrentPhase()).isEqualTo(SessionPhase.QUESTION_OPEN);
        assertThat(sessionQuizQuery.effectiveQuiz(session).questions())
                .extracting(PlayableQuestion::questionId).containsExactly(Q1, Q2);
        assertThat(removedEvent().wasInPlay()).isFalse();
    }

    @Test
    void removingTheQuestionInPlayWithNoAnswersCancelsItAndPreviewsTheNext() {
        Session session = sessionOnQuestion(Q1);
        stubQuiz();
        stubParticipants(participantWithNoAnswer());

        service.remove(hostUser, session.getId(), Q1);

        assertThat(session.getCurrentQuestionId()).isEqualTo(Q2);
        assertThat(session.getCurrentPhase()).isEqualTo(SessionPhase.QUESTION_PREVIEW);
        // The next question gets a real reading period, armed on the server.
        verify(timerScheduler).schedulePreviewEnd(eq(session.getId()), eq(Q2), any(), eq(30));
        assertThat(previewEvent().questionId()).isEqualTo(Q2);
        assertThat(session.removedQuestions().getFirst().cancelledAnswerCount()).isZero();
    }

    @Test
    void removingAnAnsweredQuestionReversesEveryAnswerAndEveryPoint() {
        Session session = sessionOnQuestion(Q1);
        stubQuiz();
        Participant answered = participantWithNoAnswer();
        answered.recordAnswer(new ParticipantAnswer(Q1, Set.of(OPTION), EN, NOW, 900, 850));
        Participant alsoAnswered = participantWithNoAnswer();
        alsoAnswered.recordAnswer(new ParticipantAnswer(Q1, Set.of(OPTION), EN, NOW, 400, 950));
        stubParticipants(answered, alsoAnswered);

        service.remove(hostUser, session.getId(), Q1);

        // Nothing of the question survives on the participants, so every
        // projection over their answers — standings, distribution, progress
        // — is correct without any of them being told about the removal.
        assertThat(answered.answers()).isEmpty();
        assertThat(answered.getTotalScore()).isZero();
        assertThat(alsoAnswered.answers()).isEmpty();
        assertThat(alsoAnswered.getTotalScore()).isZero();
        verify(participantRepository).saveAll(any());
        assertThat(session.removedQuestions().getFirst().cancelledAnswerCount()).isEqualTo(2);
        assertThat(session.removedQuestions().getFirst().removedFromPhase())
                .isEqualTo(SessionPhase.QUESTION_OPEN);
    }

    @Test
    void answersToOtherQuestionsAreLeftExactlyAsTheyWere() {
        Session session = sessionOnQuestion(Q2);
        stubQuiz();
        Participant participant = participantWithNoAnswer();
        participant.recordAnswer(new ParticipantAnswer(Q1, Set.of(OPTION), EN, NOW, 900, 800));
        participant.recordAnswer(new ParticipantAnswer(Q2, Set.of(OPTION), EN, NOW, 500, 900));
        stubParticipants(participant);

        service.remove(hostUser, session.getId(), Q2);

        assertThat(participant.answers()).extracting(ParticipantAnswer::questionId).containsExactly(Q1);
        assertThat(participant.getTotalScore()).isEqualTo(800);
    }

    @Test
    void removingTheLastQuestionStillToPlayFinishesTheSessionWithItsStandings() {
        Session session = sessionOnQuestion(Q3);
        stubQuiz();
        stubParticipants(participantWithNoAnswer());

        service.remove(hostUser, session.getId(), Q3);

        // No leaderboard for a question nobody completed — straight to the
        // ceremony, with history captured on the way.
        assertThat(session.getState()).isEqualTo(SessionState.FINISHED);
        assertThat(session.isFinalResultsReleased()).isFalse();
        verify(finalStandingRepository).saveAll(any());
        verify(eventPublisher).publish(any(SessionFinishedEvent.class));
        verify(eventPublisher, never()).publish(any(QuestionPreviewStartedEvent.class));
    }

    @Test
    void refusesToEmptyTheSessionOfQuestionsEntirely() {
        Session session = sessionOnQuestion(Q1);
        when(gameplayQuizQuery.load(any()))
                .thenReturn(new PlayableQuizView(30, List.of(playable(Q1))));

        assertThatExceptionOfType(QuestionRemovalNotAllowedException.class)
                .isThrownBy(() -> service.remove(hostUser, session.getId(), Q1));
        assertThat(session.isRemoved(Q1)).isFalse();
        assertThat(session.getState()).isEqualTo(SessionState.IN_PROGRESS);
    }

    @Test
    void aSecondRemovalOfTheSameQuestionConvergesInsteadOfConflicting() {
        Session session = sessionOnQuestion(Q1);
        stubQuiz();
        stubParticipants(participantWithNoAnswer());
        service.remove(hostUser, session.getId(), Q1);

        SessionSummaryView second = service.remove(hostUser, session.getId(), Q1);

        // The losing click must not throw, must not restore the question,
        // and above all must not advance play a second time.
        assertThat(second.currentQuestionId()).isEqualTo(Q2);
        assertThat(session.removedQuestions()).hasSize(1);
        verify(eventPublisher, times(1)).publish(any(QuestionRemovedEvent.class));
    }

    @Test
    void aStalePreviewTimerCannotReopenARemovedQuestion() {
        Session session = sessionOnQuestion(Q1);
        stubQuiz();
        stubParticipants(participantWithNoAnswer());
        service.remove(hostUser, session.getId(), Q1);
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));

        // The callback armed before the removal fires afterwards. It is
        // aimed at a question that is no longer current, which is exactly
        // what the guard checks.
        Optional<Instant> reopened = new OpenQuestionApplicationService(
                sessionRepository, eventPublisher, CLOCK)
                .openIfPreviewExpired(session.getId(), Q1, 30);

        assertThat(reopened).isEmpty();
        assertThat(session.getCurrentQuestionId()).isEqualTo(Q2);
        assertThat(session.getCurrentPhase()).isEqualTo(SessionPhase.QUESTION_PREVIEW);
    }

    @Test
    void onlyTheHostingIdentityMayRemoveAQuestion() {
        Session session = sessionOnQuestion(Q1);

        assertThatExceptionOfType(NotSessionHostException.class)
                .isThrownBy(() -> service.remove(host(), session.getId(), Q1));
        assertThat(session.isRemoved(Q1)).isFalse();
    }

    // --- fixtures ---

    private Session sessionOnQuestion(UUID questionId) {
        Session session = sessionHostedBy(hostUser, "700001");
        session.openLobby();
        session.registerParticipant(UUID.randomUUID(),
                ParticipantKey.forGuest(GuestParticipantToken.generate()));
        session.start();
        session.previewQuestion(questionId, QuestionTimer.startingAt(NOW, Duration.ofSeconds(5)));
        session.openQuestion(QuestionTimer.startingAt(NOW, Duration.ofSeconds(30)));
        when(sessionRepository.findAndLockById(session.getId())).thenReturn(Optional.of(session));
        return session;
    }

    private void stubQuiz() {
        when(gameplayQuizQuery.load(any())).thenReturn(new PlayableQuizView(30,
                List.of(playable(Q1), playable(Q2), playable(Q3))));
    }

    private static PlayableQuestion playable(UUID questionId) {
        return new PlayableQuestion(questionId, Difficulty.EASY, Set.of(OPTION), Set.of(OPTION));
    }

    private Participant participantWithNoAnswer() {
        Participant participant = Participant.guest(UUID.randomUUID(),
                GuestParticipantToken.generate(), "Guest", EN);
        participant.connect(NOW);
        return participant;
    }

    private void stubParticipants(Participant... participants) {
        when(participantRepository.findBySessionId(any()))
                .thenReturn(new ArrayList<>(List.of(participants)));
        when(finalStandingRepository.saveAll(any())).thenReturn(List.<FinalStanding>of());
    }

    private QuestionRemovedEvent removedEvent() {
        return onlyEventOfType(QuestionRemovedEvent.class);
    }

    private QuestionPreviewStartedEvent previewEvent() {
        return onlyEventOfType(QuestionPreviewStartedEvent.class);
    }

    /**
     * One removal publishes more than one event — a preview or a finish
     * alongside the removal itself — so events are captured as the port
     * declares them and picked out by type, never by invocation count.
     */
    private <T extends DomainEvent> T onlyEventOfType(Class<T> type) {
        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher, atLeastOnce()).publish(captor.capture());
        return captor.getAllValues().stream()
                .filter(type::isInstance)
                .map(type::cast)
                .reduce((first, second) -> second)
                .orElseThrow(() -> new AssertionError("no " + type.getSimpleName() + " was published"));
    }
}
