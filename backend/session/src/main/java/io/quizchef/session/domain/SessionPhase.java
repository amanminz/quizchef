package io.quizchef.session.domain;

/**
 * The gameplay loop within a running session (PRD: Running → Reveal →
 * Leaderboard → Running).
 *
 * <p><strong>Modeled only.</strong> This PR gives the phases a typed home on
 * {@link Session#getCurrentPhase()} but drives no transitions — the
 * progression flow (a later gameplay PR) owns that. The value exists so
 * gameplay does not have to reshape the aggregate to introduce it.
 */
public enum SessionPhase {
    QUESTION,
    REVEAL,
    LEADERBOARD
}
