package io.quizchef.websocket.api.event;

import io.quizchef.websocket.api.ProtocolPayload;
import java.util.List;
import java.util.UUID;

/**
 * The body of {@code leaderboard.updated}: the standings as projected at that
 * moment. Never persisted — recomputed each time it is shown (ADR-006).
 */
public record LeaderboardPayload(List<Row> entries) implements ProtocolPayload {

    public record Row(UUID participantId, String displayName, int score, int rank) {
    }
}
