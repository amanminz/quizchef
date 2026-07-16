package io.quizchef.session.application;

import static io.quizchef.session.application.SessionOrchestrationTestFixtures.anonymous;
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
import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.session.domain.Session;
import io.quizchef.session.domain.SessionSettings;
import io.quizchef.session.domain.SessionState;
import io.quizchef.session.domain.event.ParticipantJoinedEvent;
import io.quizchef.session.domain.exception.InvalidSessionTransitionException;
import io.quizchef.session.domain.exception.ParticipantAlreadyJoinedException;
import io.quizchef.session.domain.exception.SessionFullException;
import io.quizchef.session.domain.exception.SessionNotFoundException;
import io.quizchef.session.infrastructure.persistence.ParticipantRepository;
import io.quizchef.session.infrastructure.persistence.SessionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JoinSessionApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-16T10:00:00Z");

    private final SessionRepository sessionRepository = mock(SessionRepository.class);
    private final ParticipantRepository participantRepository = mock(ParticipantRepository.class);
    private final DomainEventPublisher eventPublisher = mock(DomainEventPublisher.class);
    private final JoinSessionApplicationService service = new JoinSessionApplicationService(
            sessionRepository, participantRepository, eventPublisher, Clock.fixed(NOW, ZoneOffset.UTC));

    private final CurrentUser hostUser = host();

    private Session lobby(String pin) {
        Session session = sessionHostedBy(hostUser, pin);
        session.openLobby();
        when(sessionRepository.findBySessionPinValueAndStateNot(eqPin(pin), any()))
                .thenReturn(Optional.of(session));
        return session;
    }

    private static String eqPin(String pin) {
        return org.mockito.ArgumentMatchers.eq(pin);
    }

    @Test
    void anonymousCallerJoinsAsAGuestAndReceivesAToken() {
        Session session = lobby("100001");

        ParticipantSessionView view = service.join(anonymous(),
                new JoinSessionCommand("100001", "Guest Aman", "kn"));

        assertThat(view.guestParticipantToken()).isNotBlank();
        assertThat(view.sessionState()).isEqualTo(SessionState.LOBBY);
        assertThat(session.participantCount()).isEqualTo(1);
        verify(participantRepository).save(any());

        var event = org.mockito.ArgumentCaptor.forClass(ParticipantJoinedEvent.class);
        verify(eventPublisher).publish(event.capture());
        assertThat(event.getValue().sessionId()).isEqualTo(session.getId());
    }

    @Test
    void registeredCallerJoinsBackedByTheirIdentityWithNoGuestToken() {
        CurrentUser player = host();
        lobby("100002");

        ParticipantSessionView view = service.join(player,
                new JoinSessionCommand("100002", "Aman", "en"));

        assertThat(view.guestParticipantToken()).isNull();
    }

    @Test
    void unknownPinIsNotFound() {
        when(sessionRepository.findBySessionPinValueAndStateNot(any(), any())).thenReturn(Optional.empty());

        assertThatExceptionOfType(SessionNotFoundException.class)
                .isThrownBy(() -> service.join(anonymous(), new JoinSessionCommand("999999", "G", "en")));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void joiningAClosedLobbyIsRejected() {
        Session session = sessionHostedBy(hostUser, "100003"); // still CREATED
        when(sessionRepository.findBySessionPinValueAndStateNot(any(), any()))
                .thenReturn(Optional.of(session));

        assertThatExceptionOfType(InvalidSessionTransitionException.class)
                .isThrownBy(() -> service.join(anonymous(), new JoinSessionCommand("100003", "G", "en")));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void joiningAFullSessionIsRejected() {
        Session session = sessionHostedBy(hostUser, "100004", new SessionSettings(true, true, true, 1));
        session.openLobby();
        session.registerParticipant(UUID.randomUUID(),
                io.quizchef.session.domain.ParticipantKey.forGuest(
                        io.quizchef.session.domain.GuestParticipantToken.generate()));
        when(sessionRepository.findBySessionPinValueAndStateNot(any(), any()))
                .thenReturn(Optional.of(session));

        assertThatExceptionOfType(SessionFullException.class)
                .isThrownBy(() -> service.join(anonymous(), new JoinSessionCommand("100004", "G", "en")));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void sameRegisteredIdentityCannotJoinTwice() {
        CurrentUser player = host();
        Session session = lobby("100005");
        session.registerParticipant(UUID.randomUUID(),
                io.quizchef.session.domain.ParticipantKey.forIdentity(player.reference()));

        assertThatExceptionOfType(ParticipantAlreadyJoinedException.class)
                .isThrownBy(() -> service.join(player, new JoinSessionCommand("100005", "Aman", "en")));
    }
}
