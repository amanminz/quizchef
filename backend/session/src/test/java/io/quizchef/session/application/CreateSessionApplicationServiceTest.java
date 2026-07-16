package io.quizchef.session.application;

import static io.quizchef.session.application.SessionOrchestrationTestFixtures.QUIZ_VERSION;
import static io.quizchef.session.application.SessionOrchestrationTestFixtures.host;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.quizchef.common.event.DomainEventPublisher;
import io.quizchef.common.exception.ForbiddenException;
import io.quizchef.identity.application.AuthorizationService;
import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.identity.domain.Permission;
import io.quizchef.quiz.application.QuizPublicationQuery;
import io.quizchef.quiz.domain.exception.QuizNotPublishedException;
import io.quizchef.session.domain.SessionPin;
import io.quizchef.session.domain.SessionState;
import io.quizchef.session.domain.event.SessionCreatedEvent;
import io.quizchef.session.infrastructure.persistence.SessionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CreateSessionApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-16T10:00:00Z");

    private final SessionRepository sessionRepository = mock(SessionRepository.class);
    private final QuizPublicationQuery quizPublicationQuery = mock(QuizPublicationQuery.class);
    private final SessionCodeGenerator codeGenerator = mock(SessionCodeGenerator.class);
    private final AuthorizationService authorizationService = mock(AuthorizationService.class);
    private final DomainEventPublisher eventPublisher = mock(DomainEventPublisher.class);
    private final CreateSessionApplicationService service = new CreateSessionApplicationService(
            sessionRepository, quizPublicationQuery, codeGenerator, authorizationService,
            eventPublisher, Clock.fixed(NOW, ZoneOffset.UTC));

    private final CurrentUser caller = host();

    @Test
    void createsAHostedSessionWithAUniquePin() {
        when(codeGenerator.generate()).thenReturn(SessionPin.of("424242"));
        when(sessionRepository.existsBySessionPinValueAndStateNot(any(), any())).thenReturn(false);

        SessionSummaryView view = service.create(caller,
                new CreateSessionCommand(QUIZ_VERSION));

        assertThat(view.state()).isEqualTo(SessionState.CREATED);
        assertThat(view.sessionPin()).isEqualTo("424242");
        assertThat(view.hostIdentityId()).isEqualTo(caller.identityId());
        assertThat(view.publishedQuizVersionId()).isEqualTo(QUIZ_VERSION);
        verify(authorizationService).authorize(caller, Permission.QUIZ_HOST);
        verify(quizPublicationQuery).requirePublished(QUIZ_VERSION);

        ArgumentCaptor<SessionCreatedEvent> event = ArgumentCaptor.forClass(SessionCreatedEvent.class);
        verify(eventPublisher).publish(event.capture());
        assertThat(event.getValue().sessionId()).isEqualTo(view.sessionId());
        assertThat(event.getValue().occurredAt()).isEqualTo(NOW);
    }

    @Test
    void retriesUntilThePinIsFreeAmongActiveSessions() {
        when(codeGenerator.generate())
                .thenReturn(SessionPin.of("111111"))
                .thenReturn(SessionPin.of("222222"));
        when(sessionRepository.existsBySessionPinValueAndStateNot(eq("111111"), any())).thenReturn(true);
        when(sessionRepository.existsBySessionPinValueAndStateNot(eq("222222"), any())).thenReturn(false);

        SessionSummaryView view = service.create(caller, new CreateSessionCommand(QUIZ_VERSION));

        assertThat(view.sessionPin()).isEqualTo("222222");
    }

    @Test
    void deniedAuthorizationCreatesNothing() {
        doThrow(new ForbiddenException()).when(authorizationService)
                .authorize(caller, Permission.QUIZ_HOST);

        assertThatExceptionOfType(ForbiddenException.class)
                .isThrownBy(() -> service.create(caller, new CreateSessionCommand(QUIZ_VERSION)));
        verifyNoInteractions(quizPublicationQuery, codeGenerator, eventPublisher);
        verify(sessionRepository, never()).saveAndFlush(any());
    }

    @Test
    void unpublishedQuizIsRejectedBeforeAnyPinIsAllocated() {
        doThrow(new QuizNotPublishedException(QUIZ_VERSION)).when(quizPublicationQuery)
                .requirePublished(QUIZ_VERSION);

        assertThatExceptionOfType(QuizNotPublishedException.class)
                .isThrownBy(() -> service.create(caller, new CreateSessionCommand(QUIZ_VERSION)));
        verifyNoInteractions(codeGenerator, eventPublisher);
        verify(sessionRepository, never()).existsBySessionPinValueAndStateNot(anyString(), any());
    }
}
