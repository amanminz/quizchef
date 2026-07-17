package io.quizchef.session.domain;

import java.util.UUID;

/**
 * One ranked row of a leaderboard projection: who, their display name, their
 * score, and their position. Never persisted — the leaderboard is recomputed
 * from participants' cached scores whenever it is shown (ADR-006).
 */
public record LeaderboardEntry(
        UUID participantId,
        String displayName,
        int score,
        int rank
) {
}
