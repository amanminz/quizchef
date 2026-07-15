package io.quizchef.quiz.domain.event;

import io.quizchef.common.event.DomainEvent;
import io.quizchef.identity.domain.IdentityReference;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A quiz was created.
 */
public record QuizCreatedEvent(
        UUID quizId,
        IdentityReference owner,
        Instant occurredAt
) implements DomainEvent {

    public QuizCreatedEvent {
        Objects.requireNonNull(quizId, "quizId must not be null");
        Objects.requireNonNull(owner, "owner must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
