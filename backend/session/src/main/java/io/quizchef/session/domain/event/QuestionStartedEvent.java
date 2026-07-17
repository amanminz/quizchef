package io.quizchef.session.domain.event;

import io.quizchef.common.event.DomainEvent;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A question opened for answering, closing at {@code endsAt}. Carries the
 * timer so clients can render the countdown — the server remains the
 * authority on when the question actually closes (ADR-006).
 */
public record QuestionStartedEvent(
        UUID sessionId,
        UUID questionId,
        Instant endsAt,
        int durationSeconds,
        Instant occurredAt
) implements DomainEvent {

    public QuestionStartedEvent {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(questionId, "questionId must not be null");
        Objects.requireNonNull(endsAt, "endsAt must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
