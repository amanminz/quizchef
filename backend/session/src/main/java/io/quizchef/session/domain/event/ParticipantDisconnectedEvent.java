package io.quizchef.session.domain.event;

import io.quizchef.common.event.DomainEvent;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A participant's connection dropped. The participant is retained
 * (ADR-003); only its connectivity changed.
 */
public record ParticipantDisconnectedEvent(
        UUID sessionId,
        UUID participantId,
        Instant occurredAt
) implements DomainEvent {

    public ParticipantDisconnectedEvent {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(participantId, "participantId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
