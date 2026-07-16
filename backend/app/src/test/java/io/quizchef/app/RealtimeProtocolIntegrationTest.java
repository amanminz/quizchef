package io.quizchef.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import io.quizchef.common.event.DomainEventPublisher;
import io.quizchef.session.domain.event.ParticipantReconnectedEvent;
import io.quizchef.session.domain.event.SessionStartedEvent;
import io.quizchef.websocket.api.ProtocolMessage;
import io.quizchef.websocket.api.ProtocolMessageType;
import io.quizchef.websocket.api.Topics;
import io.quizchef.websocket.api.event.ParticipantPayload;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The realtime path end to end inside the real Spring context: the WebSocket
 * STOMP configuration loads, and a published session domain event travels
 * projector → protocol → STOMP adapter → broker, landing on the right topic
 * as the right protocol message. The broker template is mocked — this
 * verifies the wiring and the destination, not a live socket.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class RealtimeProtocolIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockBean
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private DomainEventPublisher domainEventPublisher;

    @Test
    void projectsASessionEventOntoItsSessionTopic() {
        UUID sessionId = UUID.randomUUID();

        domainEventPublisher.publish(new SessionStartedEvent(sessionId,
                Instant.parse("2026-07-16T10:00:00Z")));

        ArgumentCaptor<ProtocolMessage> message = ArgumentCaptor.forClass(ProtocolMessage.class);
        verify(messagingTemplate).convertAndSend(eq(Topics.session(sessionId)), message.capture());
        assertThat(message.getValue().type()).isEqualTo(ProtocolMessageType.SESSION_STARTED);
        assertThat(message.getValue().sessionId()).isEqualTo(sessionId);
        assertThat(message.getValue().protocolVersion()).isEqualTo(1);
    }

    @Test
    void projectsAParticipantEventWithItsPayload() {
        UUID sessionId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();

        domainEventPublisher.publish(new ParticipantReconnectedEvent(sessionId, participantId,
                Instant.parse("2026-07-16T10:05:00Z")));

        ArgumentCaptor<ProtocolMessage> message = ArgumentCaptor.forClass(ProtocolMessage.class);
        verify(messagingTemplate).convertAndSend(eq(Topics.session(sessionId)), message.capture());
        assertThat(message.getValue().type()).isEqualTo(ProtocolMessageType.PARTICIPANT_RECONNECTED);
        assertThat(((ParticipantPayload) message.getValue().payload()).participantId())
                .isEqualTo(participantId);
    }

    @Test
    void convertAndSendReceivesAProtocolMessageNeverADomainEvent() {
        domainEventPublisher.publish(new SessionStartedEvent(UUID.randomUUID(), Instant.now()));

        verify(messagingTemplate).convertAndSend(any(String.class), any(ProtocolMessage.class));
    }
}
