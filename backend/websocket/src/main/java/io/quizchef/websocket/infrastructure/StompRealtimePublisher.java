package io.quizchef.websocket.infrastructure;

import io.quizchef.websocket.api.ProtocolMessage;
import io.quizchef.websocket.api.Topics;
import io.quizchef.websocket.application.RealtimePublisher;
import java.util.UUID;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * The one class that knows STOMP exists. It implements the {@link
 * RealtimePublisher} port by resolving the destination through {@link Topics}
 * and handing the message to Spring's {@link SimpMessagingTemplate}.
 *
 * <p>Pure delivery: no mapping, no business decisions. Replace it with an SSE
 * or MQTT adapter and nothing upstream changes (ADR-004).
 */
@Component
public class StompRealtimePublisher implements RealtimePublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public StompRealtimePublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void publish(ProtocolMessage message) {
        messagingTemplate.convertAndSend(Topics.session(message.sessionId()), message);
    }

    @Override
    public void publishToParticipant(UUID participantId, ProtocolMessage message) {
        messagingTemplate.convertAndSend(Topics.participant(participantId), message);
    }

    @Override
    public void publishToHost(UUID sessionId, ProtocolMessage message) {
        messagingTemplate.convertAndSend(Topics.host(sessionId), message);
    }
}
