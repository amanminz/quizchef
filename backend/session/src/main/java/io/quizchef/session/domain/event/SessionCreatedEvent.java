package io.quizchef.session.domain.event;

import io.quizchef.common.event.DomainEvent;
import io.quizchef.identity.domain.IdentityReference;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A session was created for a published quiz version, ready to open a lobby.
 */
public record SessionCreatedEvent(
        UUID sessionId,
        IdentityReference host,
        UUID publishedQuizVersionId,
        Instant occurredAt
) implements DomainEvent {

    public SessionCreatedEvent {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(host, "host must not be null");
        Objects.requireNonNull(publishedQuizVersionId, "publishedQuizVersionId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
