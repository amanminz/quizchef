package io.quizchef.identity.domain.event;

import io.quizchef.common.event.DomainEvent;
import io.quizchef.identity.domain.IdentityReference;
import io.quizchef.identity.domain.Permission;
import java.time.Instant;
import java.util.Objects;

/**
 * An identity was granted a permission.
 *
 * <p>Published only for successful authorization; the denial path publishes
 * {@link IdentityAuthorizationDeniedEvent} instead (Phase 3 PR #3). Carries
 * only the reference and the permission; no PII.
 */
public record IdentityAuthorizedEvent(
        IdentityReference identity,
        Permission permission,
        Instant occurredAt
) implements DomainEvent {

    public IdentityAuthorizedEvent {
        Objects.requireNonNull(identity, "identity must not be null");
        Objects.requireNonNull(permission, "permission must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
