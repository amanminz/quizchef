package io.quizchef.session.application;

import static io.quizchef.session.application.SessionOrchestrationTestFixtures.host;
import static io.quizchef.session.application.SessionOrchestrationTestFixtures.sessionHostedBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.quizchef.identity.application.AuthorizationService;
import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.quiz.domain.LanguageCode;
import io.quizchef.session.domain.GuestParticipantToken;
import io.quizchef.session.domain.Participant;
import io.quizchef.session.domain.ParticipantAnswer;
import io.quizchef.session.domain.QuestionTimer;
import io.quizchef.session.domain.Session;
import io.quizchef.session.domain.exception.NoCurrentQuestionException;
import io.quizchef.session.domain.exception.NotSessionHostException;
import io.quizchef.session.infrastructure.persistence.ParticipantRepository;
import io.quizchef.session.infrastructure.persistence.SessionRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AnswerProgressQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-19T10:00:00Z");

    private final SessionRepository sessionRepository = mock(SessionRepository.class);
    private final ParticipantRepository participantRepository = mock(ParticipantRepository.class);
    private final AuthorizationService authorizationService = mock(AuthorizationService.class);
    private final AnswerProgressQueryService service = new AnswerProgressQueryService(
            sessionRepository, participantRepository, authorizationService);

    private final CurrentUser hostUser = host();
    private final UUID questionId = UUID.randomUUID();

    private Session sessionWithOpenQuestion() {
        Session session = sessionHostedBy(hostUser, "042317");
        session.openLobby();
        Participant seed = connectedParticipant(session);
        session.start();
        session.openQuestion(questionId, QuestionTimer.startingAt(NOW, Duration.ofSeconds(30)));
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(participantRepository.findBySessionId(session.getId()))
                .thenReturn(List.of(seed));
        return session;
    }

    private Participant connectedParticipant(Session session) {
        Participant participant = Participant.guest(session.getId(),
                GuestParticipantToken.generate(), "Player " + UUID.randomUUID(), LanguageCode.of("en"));
        session.registerParticipant(participant.getId(), participant.key());
        participant.connect(NOW);
        return participant;
    }

    private ParticipantAnswer answerFor(UUID answeredQuestionId) {
        return new ParticipantAnswer(answeredQuestionId, Set.of(UUID.randomUUID()),
                LanguageCode.of("en"), NOW, 1_000L, 100);
    }

    @Test
    void countsAcceptedAnswersOnceAgainstTheEligibleParticipants() {
        Session session = sessionWithOpenQuestion();
        Participant answered = connectedParticipant(session);
        answered.recordAnswer(answerFor(questionId));
        Participant thinking = connectedParticipant(session);
        // An answer for another question never counts for this one.
        Participant answeredEarlier = connectedParticipant(session);
        answeredEarlier.recordAnswer(answerFor(UUID.randomUUID()));
        when(participantRepository.findBySessionId(session.getId()))
                .thenReturn(List.of(answered, thinking, answeredEarlier));

        AnswerProgressView progress = service.progress(hostUser, session.getId());

        assertThat(progress.questionId()).isEqualTo(questionId);
        assertThat(progress.answeredCount()).isEqualTo(1);
        assertThat(progress.eligibleCount()).isEqualTo(3);
    }

    @Test
    void aDisconnectedParticipantStopsBeingEligibleUnlessTheirAnswerIsIn() {
        Session session = sessionWithOpenQuestion();
        Participant answeredThenDropped = connectedParticipant(session);
        answeredThenDropped.recordAnswer(answerFor(questionId));
        answeredThenDropped.disconnect(NOW);
        Participant droppedWithoutAnswering = connectedParticipant(session);
        droppedWithoutAnswering.disconnect(NOW);
        when(participantRepository.findBySessionId(session.getId()))
                .thenReturn(List.of(answeredThenDropped, droppedWithoutAnswering));

        AnswerProgressView progress = service.progress(hostUser, session.getId());

        // The answered count can never exceed the eligible count.
        assertThat(progress.answeredCount()).isEqualTo(1);
        assertThat(progress.eligibleCount()).isEqualTo(1);
    }

    @Test
    void aLateJoinerGrowsTheEligibleCountImmediately() {
        Session session = sessionWithOpenQuestion();
        Participant early = connectedParticipant(session);
        Participant late = connectedParticipant(session);
        when(participantRepository.findBySessionId(session.getId()))
                .thenReturn(List.of(early, late));

        assertThat(service.progress(hostUser, session.getId()).eligibleCount()).isEqualTo(2);
    }

    @Test
    void requiresAQuestionInPlay() {
        Session session = sessionHostedBy(hostUser, "042317");
        session.openLobby();
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));

        assertThatExceptionOfType(NoCurrentQuestionException.class)
                .isThrownBy(() -> service.progress(hostUser, session.getId()));
    }

    @Test
    void onlyTheHostReadsProgress() {
        Session session = sessionWithOpenQuestion();

        assertThatExceptionOfType(NotSessionHostException.class)
                .isThrownBy(() -> service.progress(host(), session.getId()));
    }
}
