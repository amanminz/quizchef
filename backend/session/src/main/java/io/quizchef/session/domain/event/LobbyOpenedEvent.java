package io.quizchef.session.domain.event;

import io.quizchef.common.event.DomainEvent;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A session opened its lobby; participants may now join.
 */
public record LobbyOpenedEvent(
        UUID sessionId,
        Instant occurredAt
) implements DomainEvent {

    public LobbyOpenedEvent {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
