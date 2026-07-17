package io.quizchef.session.application;

import static io.quizchef.session.application.SessionOrchestrationTestFixtures.anonymous;
import static io.quizchef.session.application.SessionOrchestrationTestFixtures.host;
import static io.quizchef.session.application.SessionOrchestrationTestFixtures.sessionHostedBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.quizchef.common.event.DomainEventPublisher;
import io.quizchef.common.exception.UnauthorizedException;
import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.quiz.domain.LanguageCode;
import io.quizchef.session.domain.GuestParticipantToken;
import io.quizchef.session.domain.Participant;
import io.quizchef.session.domain.ParticipantState;
import io.quizchef.session.domain.Session;
import io.quizchef.session.domain.event.ParticipantReconnectedEvent;
import io.quizchef.session.domain.exception.ParticipantNotFoundException;
import io.quizchef.session.infrastructure.persistence.ParticipantRepository;
import io.quizchef.session.infrastructure.persistence.SessionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReconnectParticipantApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-16T10:05:00Z");
    private static final LanguageCode EN = LanguageCode.of("en");

    private final SessionRepository sessionRepository = mock(SessionRepository.class);
    private final ParticipantRepository participantRepository = mock(ParticipantRepository.class);
    private final DomainEventPublisher eventPublisher = mock(DomainEventPublisher.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final SessionSnapshotAssembler snapshotAssembler = new SessionSnapshotAssembler(
            participantRepository, new io.quizchef.session.domain.LeaderboardService(), clock);
    private final ReconnectParticipantApplicationService service =
            new ReconnectParticipantApplicationService(sessionRepository, participantRepository,
                    snapshotAssembler, eventPublisher, clock);

    private final CurrentUser hostUser = host();

    private Session storedSession(String pin) {
        Session session = sessionHostedBy(hostUser, pin);
        session.openLobby();
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        return session;
    }

    @Test
    void guestReconnectsByTokenAndReceivesASnapshot() {
        Session session = storedSession("200001");
        GuestParticipantToken token = GuestParticipantToken.generate();
        Participant guest = Participant.guest(session.getId(), token, "Guest", EN);
        when(participantRepository.findByGuestParticipantTokenValue(token.value()))
                .thenReturn(Optional.of(guest));

        SessionSnapshotView snapshot = service.reconnect(anonymous(),
                new ReconnectSessionCommand(null, token.value()));

        assertThat(guest.getState()).isEqualTo(ParticipantState.CONNECTED);
        assertThat(snapshot.sessionId()).isEqualTo(session.getId());
        assertThat(snapshot.participantId()).isEqualTo(guest.getId());
        assertThat(snapshot.sessionState()).isEqualTo("LOBBY");
        assertThat(snapshot.participantScore()).isZero();

        var event = org.mockito.ArgumentCaptor.forClass(ParticipantReconnectedEvent.class);
        verify(eventPublisher).publish(event.capture());
        assertThat(event.getValue().participantId()).isEqualTo(guest.getId());
    }

    @Test
    void registeredParticipantReconnectsBySessionAndIdentity() {
        Session session = storedSession("200002");
        Participant registered = Participant.registered(session.getId(), hostUser.reference(), "Aman", EN);
        when(participantRepository.findBySessionIdAndIdentityReferenceIdentityId(
                session.getId(), hostUser.identityId())).thenReturn(Optional.of(registered));

        SessionSnapshotView snapshot = service.reconnect(hostUser,
                new ReconnectSessionCommand(session.getId(), null));

        assertThat(registered.getState()).isEqualTo(ParticipantState.CONNECTED);
        assertThat(snapshot.participantId()).isEqualTo(registered.getId());
    }

    @Test
    void unknownGuestTokenIsNotFound() {
        when(participantRepository.findByGuestParticipantTokenValue(any())).thenReturn(Optional.empty());

        assertThatExceptionOfType(ParticipantNotFoundException.class)
                .isThrownBy(() -> service.reconnect(anonymous(),
                        new ReconnectSessionCommand(null, "no-such-token")));
    }

    @Test
    void registeredReconnectWithoutIdentityIsUnauthorized() {
        assertThatExceptionOfType(UnauthorizedException.class)
                .isThrownBy(() -> service.reconnect(anonymous(),
                        new ReconnectSessionCommand(UUID.randomUUID(), null)));
    }
}
