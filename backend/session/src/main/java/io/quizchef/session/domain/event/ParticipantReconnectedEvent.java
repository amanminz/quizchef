package io.quizchef.session.domain.event;

import io.quizchef.common.event.DomainEvent;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A participant rebound to the session after a disconnect, resuming its
 * preserved score and progress.
 */
public record ParticipantReconnectedEvent(
        UUID sessionId,
        UUID participantId,
        Instant occurredAt
) implements DomainEvent {

    public ParticipantReconnectedEvent {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(participantId, "participantId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
