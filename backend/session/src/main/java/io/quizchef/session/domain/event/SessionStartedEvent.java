package io.quizchef.session.domain.event;

import io.quizchef.common.event.DomainEvent;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A session left the lobby and began play.
 */
public record SessionStartedEvent(
        UUID sessionId,
        Instant occurredAt
) implements DomainEvent {

    public SessionStartedEvent {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
