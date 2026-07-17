package io.quizchef.session.domain;

/**
 * The execution phase of a running session — the gameplay loop inside
 * {@code IN_PROGRESS}.
 *
 * <pre>
 * (IN_PROGRESS, no phase) → QUESTION_OPEN → QUESTION_CLOSED → ANSWER_REVEALED → LEADERBOARD → QUESTION_OPEN … → (FINISHED)
 * </pre>
 *
 * <p>Answers are accepted only while {@code QUESTION_OPEN}. Every transition
 * happens through a {@link Session} method and is server-authoritative
 * (ADR-006); illegal transitions throw.
 */
public enum SessionPhase {
    QUESTION_OPEN,
    QUESTION_CLOSED,
    ANSWER_REVEALED,
    LEADERBOARD
}
