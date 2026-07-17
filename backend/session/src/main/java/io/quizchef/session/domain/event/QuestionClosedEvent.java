package io.quizchef.session.domain.event;

import io.quizchef.common.event.DomainEvent;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A question stopped accepting answers — the timer expired or the host
 * closed it.
 */
public record QuestionClosedEvent(
        UUID sessionId,
        UUID questionId,
        Instant occurredAt
) implements DomainEvent {

    public QuestionClosedEvent {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(questionId, "questionId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
