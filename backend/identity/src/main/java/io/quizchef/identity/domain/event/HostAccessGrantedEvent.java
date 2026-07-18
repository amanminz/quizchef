package io.quizchef.identity.domain.event;

import io.quizchef.common.event.DomainEvent;
import io.quizchef.identity.domain.IdentityReference;
import java.time.Instant;
import java.util.Objects;

/**
 * An identity was granted host access (the QUIZ_MASTER role) through the
 * onboarding flow. Published only when something actually changed — a
 * repeat request is a no-op and stays silent.
 *
 * <p>Carries only the {@link IdentityReference}, no PII, matching every
 * identity event.
 */
public record HostAccessGrantedEvent(IdentityReference identity, Instant occurredAt)
        implements DomainEvent {

    public HostAccessGrantedEvent {
        Objects.requireNonNull(identity, "identity must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
