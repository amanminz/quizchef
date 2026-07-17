package io.quizchef.session.domain.event;

import io.quizchef.common.event.DomainEvent;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A participant's answer was accepted and scored. Deliberately carries no
 * score — the acknowledgement to the participant confirms receipt only; the
 * server never returns points (ADR-006). Projected to the submitting
 * participant alone.
 */
public record AnswerSubmittedEvent(
        UUID sessionId,
        UUID participantId,
        UUID questionId,
        Instant occurredAt
) implements DomainEvent {

    public AnswerSubmittedEvent {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(participantId, "participantId must not be null");
        Objects.requireNonNull(questionId, "questionId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
