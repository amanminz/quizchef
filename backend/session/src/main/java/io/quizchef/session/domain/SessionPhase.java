package io.quizchef.session.domain;

/**
 * The execution phase of a running session — the gameplay loop inside
 * {@code IN_PROGRESS}.
 *
 * <pre>
 * (IN_PROGRESS, no phase) → QUESTION_PREVIEW → QUESTION_OPEN → QUESTION_CLOSED → ANSWER_REVEALED → LEADERBOARD → QUESTION_PREVIEW … → (FINISHED)
 * </pre>
 *
 * <p>{@code QUESTION_PREVIEW} is a short, server-timed reading period: the
 * question is current and visible, but its options are withheld and no
 * answer is accepted — {@link Session#acceptsAnswersFor} is false, exactly
 * like every other non-{@code QUESTION_OPEN} phase. Answers are accepted
 * only while {@code QUESTION_OPEN}. Every transition happens through a
 * {@link Session} method and is server-authoritative (ADR-006); illegal
 * transitions throw.
 */
public enum SessionPhase {
    QUESTION_PREVIEW,
    QUESTION_OPEN,
    QUESTION_CLOSED,
    ANSWER_REVEALED,
    LEADERBOARD
}
