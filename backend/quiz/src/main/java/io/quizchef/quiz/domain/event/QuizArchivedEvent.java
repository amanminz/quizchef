package io.quizchef.quiz.domain.event;

import io.quizchef.common.event.DomainEvent;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A quiz was retired. Archived quizzes are retained, never deleted.
 */
public record QuizArchivedEvent(
        UUID quizId,
        Instant occurredAt
) implements DomainEvent {

    public QuizArchivedEvent {
        Objects.requireNonNull(quizId, "quizId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
