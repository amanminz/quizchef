package io.quizchef.quiz.domain.event;

import io.quizchef.common.event.DomainEvent;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A quiz became publicly hostable.
 */
public record QuizPublishedEvent(
        UUID quizId,
        Instant occurredAt
) implements DomainEvent {

    public QuizPublishedEvent {
        Objects.requireNonNull(quizId, "quizId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
