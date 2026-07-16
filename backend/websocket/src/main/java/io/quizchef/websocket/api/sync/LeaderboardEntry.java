package io.quizchef.websocket.api.sync;

import java.util.UUID;

/**
 * One row of a leaderboard snapshot: a participant, their display name, their
 * score, and their rank. A wire DTO — the leaderboard's computation lives in
 * the scoring engine (RFC-006); this is only its shape on the wire.
 */
public record LeaderboardEntry(
        UUID participantId,
        String displayName,
        int score,
        int rank
) {
}
