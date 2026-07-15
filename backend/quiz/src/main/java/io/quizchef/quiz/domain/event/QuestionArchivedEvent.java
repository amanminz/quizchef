package io.quizchef.quiz.domain.event;

import io.quizchef.common.event.DomainEvent;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A question was retired: unavailable for new quizzes, while existing
 * published quizzes continue functioning. Archived questions are retained,
 * never deleted.
 */
public record QuestionArchivedEvent(
        UUID questionId,
        Instant occurredAt
) implements DomainEvent {

    public QuestionArchivedEvent {
        Objects.requireNonNull(questionId, "questionId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
