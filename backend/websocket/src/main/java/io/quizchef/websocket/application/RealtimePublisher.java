package io.quizchef.websocket.application;

import io.quizchef.websocket.api.ProtocolMessage;
import java.util.UUID;

/**
 * The outbound realtime port: "deliver this protocol message to this
 * audience." It names <em>who</em> should receive a message, never
 * <em>how</em> — no STOMP, no broker, no connection types leak through it.
 *
 * <p>The transport adapter ({@code StompRealtimePublisher}) implements it;
 * everything that emits realtime updates depends only on this interface, so
 * the transport could become SSE, MQTT, or polling without any caller
 * changing (ADR-004).
 */
public interface RealtimePublisher {

    /** Broadcast to everyone in the session. */
    void publish(ProtocolMessage message);

    /** Send to a single participant (e.g. their reconnection snapshot). */
    void publishToParticipant(UUID participantId, ProtocolMessage message);

    /** Send to the host's control channel. */
    void publishToHost(UUID sessionId, ProtocolMessage message);
}
