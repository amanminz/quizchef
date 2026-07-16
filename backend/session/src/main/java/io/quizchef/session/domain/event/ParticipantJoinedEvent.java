package io.quizchef.session.domain.event;

import io.quizchef.common.event.DomainEvent;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A participant joined a session's roster.
 */
public record ParticipantJoinedEvent(
        UUID sessionId,
        UUID participantId,
        Instant occurredAt
) implements DomainEvent {

    public ParticipantJoinedEvent {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(participantId, "participantId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
