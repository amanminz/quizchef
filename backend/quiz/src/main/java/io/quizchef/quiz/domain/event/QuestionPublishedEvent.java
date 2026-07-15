package io.quizchef.quiz.domain.event;

import io.quizchef.common.event.DomainEvent;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A question became reusable by quizzes and immutable.
 */
public record QuestionPublishedEvent(
        UUID questionId,
        Instant occurredAt
) implements DomainEvent {

    public QuestionPublishedEvent {
        Objects.requireNonNull(questionId, "questionId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
