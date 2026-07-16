package io.quizchef.websocket.api.sync;

import io.quizchef.websocket.api.ProtocolPayload;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Everything a reconnecting participant needs to resume exactly where they
 * left off (ADR-003, PRD "Welcome back"): the session's current state, the
 * question in play and its phase, how much time is left, the participant's
 * own score and submitted answer, and the current standings.
 *
 * <p><strong>Contract only.</strong> This defines the shape the reconnection
 * flow will deliver on the {@code session.snapshot} message; generating it —
 * reading the aggregates, computing the remaining time and leaderboard —
 * belongs to the {@code SessionRecoveryService} in a later PR. It is here
 * now so the protocol is complete and clients can be built against it.
 *
 * <p>State and phase cross the wire as strings, not the session module's
 * enums, so the protocol does not leak internal types.
 *
 * @param sessionState        e.g. "LOBBY", "IN_PROGRESS", "FINISHED"
 * @param currentQuestionId   the question in play, or null between questions
 * @param currentPhase        e.g. "QUESTION", "REVEAL", "LEADERBOARD", or null
 * @param remainingMillis     time left on the current question; 0 when none
 * @param participantScore    the reconnecting participant's cached score
 * @param submittedOptionIds  what they already answered this question, if any
 * @param leaderboard         the current standings
 */
public record SessionSnapshot(
        String sessionState,
        UUID currentQuestionId,
        String currentPhase,
        long remainingMillis,
        int participantScore,
        Set<UUID> submittedOptionIds,
        List<LeaderboardEntry> leaderboard
) implements ProtocolPayload {
}
