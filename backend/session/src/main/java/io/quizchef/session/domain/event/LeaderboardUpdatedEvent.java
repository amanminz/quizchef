package io.quizchef.session.domain.event;

import io.quizchef.common.event.DomainEvent;
import io.quizchef.session.domain.LeaderboardEntry;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The standings were projected and broadcast. Carries the ranking as it
 * stood at this moment — a projection, never persisted (ADR-006).
 */
public record LeaderboardUpdatedEvent(
        UUID sessionId,
        List<LeaderboardEntry> leaderboard,
        Instant occurredAt
) implements DomainEvent {

    public LeaderboardUpdatedEvent {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        leaderboard = List.copyOf(leaderboard);
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
