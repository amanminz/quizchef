package io.quizchef.session.domain.event;

import io.quizchef.common.event.DomainEvent;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * The correct answer for a question was revealed to everyone. The correct
 * option ids ride along so clients can highlight them — this is the first
 * moment correctness crosses the wire (ADR-006).
 */
public record AnswerRevealedEvent(
        UUID sessionId,
        UUID questionId,
        Set<UUID> correctOptionIds,
        Instant occurredAt
) implements DomainEvent {

    public AnswerRevealedEvent {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(questionId, "questionId must not be null");
        correctOptionIds = Set.copyOf(correctOptionIds);
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
