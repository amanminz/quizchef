package io.quizchef.websocket.api;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * The envelope every realtime message travels in, inbound or outbound.
 *
 * <p>It carries what every client and every future capability needs
 * regardless of message type:
 * <ul>
 *   <li>{@code protocolVersion} — the wire protocol version, so clients and
 *       server can evolve independently ({@link ProtocolVersion});</li>
 *   <li>{@code messageId} — a unique id per message, the basis for future
 *       duplicate detection and idempotency;</li>
 *   <li>{@code sessionId} — which session this concerns, and the routing key
 *       for topics;</li>
 *   <li>{@code occurredAt} — when the underlying fact happened, the basis for
 *       future ordering;</li>
 *   <li>{@code type} — the stable wire vocabulary ({@link
 *       ProtocolMessageType});</li>
 *   <li>{@code payload} — type-specific data, or null when the envelope says
 *       it all.</li>
 * </ul>
 *
 * <p>This is a wire DTO — never a domain entity. Domain state is always
 * mapped into a payload, never serialized directly.
 */
public record ProtocolMessage(
        int protocolVersion,
        UUID messageId,
        UUID sessionId,
        ProtocolMessageType type,
        Instant occurredAt,
        ProtocolPayload payload
) {

    public ProtocolMessage {
        Objects.requireNonNull(messageId, "messageId must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    /**
     * Builds a current-protocol message with a fresh id. {@code payload} may
     * be null for envelope-only messages.
     */
    public static ProtocolMessage of(UUID sessionId, ProtocolMessageType type,
                                     Instant occurredAt, ProtocolPayload payload) {
        return new ProtocolMessage(
                ProtocolVersion.CURRENT, UUID.randomUUID(), sessionId, type, occurredAt, payload);
    }

    /**
     * An envelope-only message (no type-specific payload).
     */
    public static ProtocolMessage of(UUID sessionId, ProtocolMessageType type, Instant occurredAt) {
        return of(sessionId, type, occurredAt, null);
    }
}
