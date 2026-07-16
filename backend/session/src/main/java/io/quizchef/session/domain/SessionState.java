package io.quizchef.session.domain;

/**
 * Lifecycle of a live session.
 *
 * <p>{@code CREATED → LOBBY → IN_PROGRESS → FINISHED → ARCHIVED}. Transitions
 * happen only through {@link Session} methods; the aggregate rejects any
 * other path. FINISHED and ARCHIVED are immutable.
 */
public enum SessionState {
    CREATED,
    LOBBY,
    IN_PROGRESS,
    FINISHED,
    ARCHIVED
}
