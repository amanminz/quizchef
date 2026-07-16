package io.quizchef.session.domain.event;

import io.quizchef.common.event.DomainEvent;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A session completed play. It is now immutable and awaits archival.
 */
public record SessionFinishedEvent(
        UUID sessionId,
        Instant occurredAt
) implements DomainEvent {

    public SessionFinishedEvent {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
