package io.quizchef.quiz.domain.event;

import io.quizchef.common.event.DomainEvent;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A question joined a quiz's composition.
 */
public record QuestionAddedToQuizEvent(
        UUID quizId,
        UUID questionId,
        Instant occurredAt
) implements DomainEvent {

    public QuestionAddedToQuizEvent {
        Objects.requireNonNull(quizId, "quizId must not be null");
        Objects.requireNonNull(questionId, "questionId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
