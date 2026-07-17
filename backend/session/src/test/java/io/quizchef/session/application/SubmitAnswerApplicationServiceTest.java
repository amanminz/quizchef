package io.quizchef.session.application;

import static io.quizchef.session.application.SessionOrchestrationTestFixtures.host;
import static io.quizchef.session.application.SessionOrchestrationTestFixtures.sessionHostedBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.quizchef.common.event.DomainEventPublisher;
import io.quizchef.quiz.application.GameplayQuizQuery;
import io.quizchef.quiz.application.PlayableQuizView;
import io.quizchef.quiz.application.PlayableQuizView.PlayableQuestion;
import io.quizchef.quiz.domain.Difficulty;
import io.quizchef.quiz.domain.LanguageCode;
import io.quizchef.session.domain.GuestParticipantToken;
import io.quizchef.session.domain.Participant;
import io.quizchef.session.domain.QuestionTimer;
import io.quizchef.session.domain.ScoringPolicy;
import io.quizchef.session.domain.ScoringService;
import io.quizchef.session.domain.Session;
import io.quizchef.session.domain.event.AnswerSubmittedEvent;
import io.quizchef.session.domain.exception.AnswerNotAcceptedException;
import io.quizchef.session.domain.exception.ParticipantNotConnectedException;
import io.quizchef.session.infrastructure.persistence.ParticipantRepository;
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

class SubmitAnswerApplicationServiceTest {

    private static final Instant START = Instant.parse("2026-07-16T10:00:00Z");
    private static final Instant ANSWERED = START.plusSeconds(6);
    private static final LanguageCode EN = LanguageCode.of("en");

    private final SessionRepository sessionRepository = mock(SessionRepository.class);
    private final ParticipantRepository participantRepository = mock(ParticipantRepository.class);
    private final GameplayQuizQuery gameplayQuizQuery = mock(GameplayQuizQuery.class);
    private final DomainEventPublisher eventPublisher = mock(DomainEventPublisher.class);
    private final SubmitAnswerApplicationService service = new SubmitAnswerApplicationService(
            sessionRepository, participantRepository, gameplayQuizQuery, new ScoringService(),
            ScoringPolicy.classic(), eventPublisher, Clock.fixed(ANSWERED, ZoneOffset.UTC));

    private final UUID questionId = UUID.randomUUID();
    private final UUID correctOption = UUID.randomUUID();
    private final UUID wrongOption = UUID.randomUUID();

    private Session openSessionWithQuestion() {
        Session session = sessionHostedBy(host(), "500001");
        session.openLobby();
        session.registerParticipant(UUID.randomUUID(),
                io.quizchef.session.domain.ParticipantKey.forGuest(GuestParticipantToken.generate()));
        session.start();
        session.openQuestion(questionId, QuestionTimer.startingAt(START, Duration.ofSeconds(30)));
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        return session;
    }

    private Participant connectedParticipant(UUID sessionId) {
        Participant participant = Participant.guest(sessionId, GuestParticipantToken.generate(), "Guest", EN);
        participant.connect(START);
        when(participantRepository.findById(participant.getId())).thenReturn(Optional.of(participant));
        return participant;
    }

    private void stubQuestion() {
        when(gameplayQuizQuery.load(any())).thenReturn(new PlayableQuizView(30, List.of(
                new PlayableQuestion(questionId, Difficulty.EASY,
                        Set.of(correctOption), Set.of(correctOption, wrongOption)))));
    }

    @Test
    void acceptsAndScoresACorrectAnswerWithoutReturningTheScore() {
        Session session = openSessionWithQuestion();
        Participant participant = connectedParticipant(session.getId());
        stubQuestion();

        AnswerAcceptedView accepted = service.submit(
                new SubmitAnswerCommand(participant.getId(), questionId, Set.of(correctOption)));

        assertThat(accepted.participantId()).isEqualTo(participant.getId());
        assertThat(accepted.questionId()).isEqualTo(questionId);
        // scored server-side and cached; 6s into a 30s question, correct, EASY
        assertThat(participant.getTotalScore()).isEqualTo(900);
        assertThat(participant.answers()).hasSize(1);

        var event = org.mockito.ArgumentCaptor.forClass(AnswerSubmittedEvent.class);
        verify(eventPublisher).publish(event.capture());
        assertThat(event.getValue().participantId()).isEqualTo(participant.getId());
    }

    @Test
    void aWrongAnswerScoresZeroButIsStillAccepted() {
        Session session = openSessionWithQuestion();
        Participant participant = connectedParticipant(session.getId());
        stubQuestion();

        service.submit(new SubmitAnswerCommand(participant.getId(), questionId, Set.of(wrongOption)));

        assertThat(participant.getTotalScore()).isZero();
        assertThat(participant.answers()).hasSize(1);
    }

    @Test
    void rejectsAnAnswerWhenNoQuestionIsOpen() {
        Session session = sessionHostedBy(host(), "500002");
        session.openLobby();
        session.registerParticipant(UUID.randomUUID(),
                io.quizchef.session.domain.ParticipantKey.forGuest(GuestParticipantToken.generate()));
        session.start();
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        Participant participant = connectedParticipant(session.getId());

        assertThatExceptionOfType(AnswerNotAcceptedException.class).isThrownBy(() ->
                service.submit(new SubmitAnswerCommand(participant.getId(), questionId, Set.of(correctOption))));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void rejectsADuplicateAnswer() {
        Session session = openSessionWithQuestion();
        Participant participant = connectedParticipant(session.getId());
        stubQuestion();
        service.submit(new SubmitAnswerCommand(participant.getId(), questionId, Set.of(correctOption)));

        assertThatExceptionOfType(AnswerNotAcceptedException.class).isThrownBy(() ->
                service.submit(new SubmitAnswerCommand(participant.getId(), questionId, Set.of(correctOption))));
    }

    @Test
    void rejectsAnAnswerFromAParticipantWhoIsNotConnected() {
        Session session = openSessionWithQuestion();
        Participant participant = Participant.guest(session.getId(),
                GuestParticipantToken.generate(), "Guest", EN); // JOINED, never connected
        when(participantRepository.findById(participant.getId())).thenReturn(Optional.of(participant));

        assertThatExceptionOfType(ParticipantNotConnectedException.class).isThrownBy(() ->
                service.submit(new SubmitAnswerCommand(participant.getId(), questionId, Set.of(correctOption))));
    }

    @Test
    void rejectsAnOptionThatDoesNotBelongToTheQuestion() {
        Session session = openSessionWithQuestion();
        Participant participant = connectedParticipant(session.getId());
        stubQuestion();

        assertThatIllegalArgumentException().isThrownBy(() ->
                service.submit(new SubmitAnswerCommand(participant.getId(), questionId, Set.of(UUID.randomUUID()))));
    }
}
