package io.quizchef.session.application;

import static io.quizchef.session.application.SessionOrchestrationTestFixtures.host;
import static io.quizchef.session.application.SessionOrchestrationTestFixtures.sessionHostedBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.quizchef.common.event.DomainEventPublisher;
import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.session.domain.GuestParticipantToken;
import io.quizchef.session.domain.ParticipantKey;
import io.quizchef.session.domain.QuestionTimer;
import io.quizchef.session.domain.Session;
import io.quizchef.session.domain.SessionPhase;
import io.quizchef.session.domain.event.QuestionStartedEvent;
import io.quizchef.session.infrastructure.persistence.SessionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The server-timed, fully automatic preview-to-open transition. There is no
 * host command that competes with it (contrast the timer-vs-host-close race
 * {@code CloseQuestionApplicationService} settles) — every "otherwise" case
 * here is simply "already moved on by the time this fired", always a
 * harmless no-op. Deliberately does not arm the answer-close timer itself
 * (see the class Javadoc on {@code OpenQuestionApplicationService} for why
 * — a circular bean dependency); it returns the answer window instead, so
 * this test asserts the returned value rather than a scheduler interaction.
 */
class OpenQuestionApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-01T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final SessionRepository sessionRepository = mock(SessionRepository.class);
    private final DomainEventPublisher eventPublisher = mock(DomainEventPublisher.class);
    private final OpenQuestionApplicationService service = new OpenQuestionApplicationService(
            sessionRepository, eventPublisher, CLOCK);

    private final CurrentUser hostUser = host();
    private final UUID questionId = UUID.randomUUID();

    private Session sessionPreviewing(UUID previewedQuestionId) {
        Session session = sessionHostedBy(hostUser, "800001");
        session.openLobby();
        session.registerParticipant(UUID.randomUUID(),
                ParticipantKey.forGuest(GuestParticipantToken.generate()));
        session.start();
        session.previewQuestion(previewedQuestionId, QuestionTimer.startingAt(NOW, Duration.ofSeconds(5)));
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        return session;
    }

    @Test
    void opensTheQuestionAndReturnsTheAnswerWindow() {
        Session session = sessionPreviewing(questionId);

        Optional<Instant> answerEndsAt = service.openIfPreviewExpired(session.getId(), questionId, 30);

        assertThat(answerEndsAt).contains(NOW.plusSeconds(30));
        assertThat(session.getCurrentPhase()).isEqualTo(SessionPhase.QUESTION_OPEN);
        assertThat(session.getCurrentQuestionId()).isEqualTo(questionId);
        assertThat(session.acceptsAnswersFor(questionId)).isTrue();
        assertThat(session.getCurrentQuestionTimer().endsAt()).isEqualTo(NOW.plusSeconds(30));

        var event = org.mockito.ArgumentCaptor.forClass(QuestionStartedEvent.class);
        verify(eventPublisher).publish(event.capture());
        assertThat(event.getValue().sessionId()).isEqualTo(session.getId());
        assertThat(event.getValue().questionId()).isEqualTo(questionId);
        assertThat(event.getValue().endsAt()).isEqualTo(NOW.plusSeconds(30));

        verify(sessionRepository).saveAndFlush(session);
    }

    @Test
    void isANoOpWhenPlayHasAlreadyMovedPastThePreview() {
        Session session = sessionPreviewing(questionId);
        session.openQuestion(QuestionTimer.startingAt(NOW, Duration.ofSeconds(30)));

        Optional<Instant> result = service.openIfPreviewExpired(session.getId(), questionId, 30);

        assertThat(result).isEmpty();
        verifyNoInteractions(eventPublisher);
        verify(sessionRepository, never()).saveAndFlush(any());
    }

    @Test
    void isANoOpWhenTheQuestionIsNoLongerTheOneBeingPreviewed() {
        Session session = sessionPreviewing(UUID.randomUUID());

        Optional<Instant> result = service.openIfPreviewExpired(session.getId(), questionId, 30);

        assertThat(result).isEmpty();
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void isANoOpForAnUnknownSession() {
        UUID unknownSessionId = UUID.randomUUID();
        when(sessionRepository.findById(unknownSessionId)).thenReturn(Optional.empty());

        Optional<Instant> result = service.openIfPreviewExpired(unknownSessionId, questionId, 30);

        assertThat(result).isEmpty();
        verifyNoInteractions(eventPublisher);
    }
}
