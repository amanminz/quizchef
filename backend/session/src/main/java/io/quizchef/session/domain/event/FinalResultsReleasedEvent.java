package io.quizchef.session.domain.event;

import io.quizchef.common.event.DomainEvent;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * The host released final standings to participants — the winner ceremony
 * has run, and every participant may now read their own final rank.
 */
public record FinalResultsReleasedEvent(
        UUID sessionId,
        Instant occurredAt
) implements DomainEvent {

    public FinalResultsReleasedEvent {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
