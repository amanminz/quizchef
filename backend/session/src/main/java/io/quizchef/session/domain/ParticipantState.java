package io.quizchef.session.domain;

/**
 * Lifecycle of a participant within a session.
 *
 * <p>{@code JOINED → CONNECTED → DISCONNECTED → FINISHED}, with reconnection
 * moving DISCONNECTED back to CONNECTED. The state survives reconnection —
 * a disconnect never deletes the participant (ADR-003, durable
 * participants).
 */
public enum ParticipantState {
    JOINED,
    CONNECTED,
    DISCONNECTED,
    FINISHED
}
