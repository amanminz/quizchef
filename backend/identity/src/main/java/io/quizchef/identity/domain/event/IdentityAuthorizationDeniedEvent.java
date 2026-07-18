package io.quizchef.identity.domain.event;

import io.quizchef.common.event.DomainEvent;
import io.quizchef.identity.domain.IdentityReference;
import io.quizchef.identity.domain.Permission;
import java.time.Instant;
import java.util.Objects;

/**
 * An authenticated identity was denied a permission it doesn't hold.
 *
 * <p>The sibling of {@link IdentityAuthorizedEvent} for the denial path
 * (Phase 3 PR #3 / RFC-011) — {@code platform}'s {@code IdentityEventLogger}
 * listens for this to log {@code security.authorization_denied}, the
 * operational signal for "why is this user getting blocked." Carries only
 * the reference and the permission; no PII. Not published for anonymous
 * callers — those fail with 401 before any permission is even checked, so
 * there is no identity to report.
 */
public record IdentityAuthorizationDeniedEvent(
        IdentityReference identity,
        Permission permission,
        Instant occurredAt
) implements DomainEvent {

    public IdentityAuthorizationDeniedEvent {
        Objects.requireNonNull(identity, "identity must not be null");
        Objects.requireNonNull(permission, "permission must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
