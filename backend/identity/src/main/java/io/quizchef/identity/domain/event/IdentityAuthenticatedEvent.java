package io.quizchef.identity.domain.event;

import io.quizchef.common.event.DomainEvent;
import io.quizchef.identity.domain.IdentityReference;
import java.time.Instant;
import java.util.Objects;

/**
 * An identity successfully authenticated.
 *
 * <p>Carries only the {@link IdentityReference} — no email, no client
 * details — so subscribers and their logs never receive PII.
 */
public record IdentityAuthenticatedEvent(IdentityReference identity, Instant occurredAt)
        implements DomainEvent {

    public IdentityAuthenticatedEvent {
        Objects.requireNonNull(identity, "identity must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
