package io.quizchef.session.application;

import static io.quizchef.session.application.SessionOrchestrationTestFixtures.host;
import static io.quizchef.session.application.SessionOrchestrationTestFixtures.sessionHostedBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.quizchef.common.event.DomainEventPublisher;
import io.quizchef.identity.application.AuthorizationService;
import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.identity.domain.Permission;
import io.quizchef.session.domain.GuestParticipantToken;
import io.quizchef.session.domain.ParticipantKey;
import io.quizchef.session.domain.Session;
import io.quizchef.session.domain.SessionState;
import io.quizchef.session.domain.event.LobbyOpenedEvent;
import io.quizchef.session.domain.event.SessionStartedEvent;
import io.quizchef.session.domain.exception.NotSessionHostException;
import io.quizchef.session.domain.exception.SessionNotStartableException;
import io.quizchef.session.infrastructure.persistence.SessionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Open-lobby and start share their host-only shape, so they are covered
 * together.
 */
class SessionLifecycleApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-16T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final SessionRepository sessionRepository = mock(SessionRepository.class);
    private final AuthorizationService authorizationService = mock(AuthorizationService.class);
    private final DomainEventPublisher eventPublisher = mock(DomainEventPublisher.class);

    private final OpenLobbyApplicationService openLobby = new OpenLobbyApplicationService(
            sessionRepository, authorizationService, eventPublisher, CLOCK);
    private final StartSessionApplicationService start = new StartSessionApplicationService(
            sessionRepository, authorizationService, eventPublisher, CLOCK);

    private final CurrentUser hostUser = host();

    @Test
    void hostOpensTheLobby() {
        Session session = sessionHostedBy(hostUser, "300001");
        when(sessionRepository.findBySessionPinValueAndStateNot(any(), any()))
                .thenReturn(Optional.of(session));

        SessionSummaryView view = openLobby.openLobby(hostUser, "300001");

        assertThat(view.state()).isEqualTo(SessionState.LOBBY);
        verify(authorizationService).authorize(hostUser, Permission.QUIZ_HOST);
        var event = org.mockito.ArgumentCaptor.forClass(LobbyOpenedEvent.class);
        verify(eventPublisher).publish(event.capture());
        assertThat(event.getValue().sessionId()).isEqualTo(session.getId());
    }

    @Test
    void aNonHostCannotOpenTheLobby() {
        Session session = sessionHostedBy(hostUser, "300002");
        when(sessionRepository.findBySessionPinValueAndStateNot(any(), any()))
                .thenReturn(Optional.of(session));

        assertThatExceptionOfType(NotSessionHostException.class)
                .isThrownBy(() -> openLobby.openLobby(host(), "300002"));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void hostStartsASessionWithParticipants() {
        Session session = sessionHostedBy(hostUser, "300003");
        session.openLobby();
        session.registerParticipant(UUID.randomUUID(),
                ParticipantKey.forGuest(GuestParticipantToken.generate()));
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));

        SessionSummaryView view = start.start(hostUser, session.getId());

        assertThat(view.state()).isEqualTo(SessionState.IN_PROGRESS);
        var event = org.mockito.ArgumentCaptor.forClass(SessionStartedEvent.class);
        verify(eventPublisher).publish(event.capture());
        assertThat(event.getValue().sessionId()).isEqualTo(session.getId());
    }

    @Test
    void startingAnEmptySessionIsRejected() {
        Session session = sessionHostedBy(hostUser, "300004");
        session.openLobby();
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));

        assertThatExceptionOfType(SessionNotStartableException.class)
                .isThrownBy(() -> start.start(hostUser, session.getId()));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void aNonHostCannotStart() {
        Session session = sessionHostedBy(hostUser, "300005");
        session.openLobby();
        session.registerParticipant(UUID.randomUUID(),
                ParticipantKey.forGuest(GuestParticipantToken.generate()));
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));

        assertThatExceptionOfType(NotSessionHostException.class)
                .isThrownBy(() -> start.start(host(), session.getId()));
        verifyNoInteractions(eventPublisher);
    }
}
