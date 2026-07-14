package io.quizchef.identity.domain.event;

import io.quizchef.common.event.DomainEvent;
import io.quizchef.identity.domain.IdentityReference;
import java.time.Instant;
import java.util.Objects;

/**
 * A new identity completed registration.
 *
 * <p>Carries only the {@link IdentityReference} — deliberately no email or
 * other personal data, so subscribers (and their logs) never receive PII.
 */
public record IdentityRegisteredEvent(IdentityReference identity, Instant occurredAt)
        implements DomainEvent {

    public IdentityRegisteredEvent {
        Objects.requireNonNull(identity, "identity must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
