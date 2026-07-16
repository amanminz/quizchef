package io.quizchef.websocket.api;

/**
 * Marker for the type-specific body of a {@link ProtocolMessage}. Lifecycle
 * messages whose meaning is fully carried by the envelope (session id, type,
 * time) have no payload; messages that need extra data carry one.
 *
 * <p>Deliberately not sealed — the protocol grows a payload type per gameplay
 * message without editing a central permit list.
 */
public interface ProtocolPayload {
}
