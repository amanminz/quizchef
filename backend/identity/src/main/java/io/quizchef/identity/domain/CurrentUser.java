package io.quizchef.identity.domain;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * The framework-independent view of who is making the current request.
 *
 * <p>Business services depend only on this abstraction — never on
 * SecurityContextHolder, Authentication objects, or JWT claims. Transport and
 * framework adapters produce it at the edge of the system.
 */
public record CurrentUser(
        UUID identityId,
        boolean authenticated,
        IdentityType identityType,
        Set<Role> roles
) {

    private static final CurrentUser ANONYMOUS = new CurrentUser(null, false, null, Set.of());

    public CurrentUser {
        roles = Set.copyOf(roles);
        if (authenticated) {
            Objects.requireNonNull(identityId, "authenticated user requires an identityId");
            Objects.requireNonNull(identityType, "authenticated user requires an identityType");
        }
    }

    public static CurrentUser anonymous() {
        return ANONYMOUS;
    }

    public static CurrentUser authenticated(UUID identityId, IdentityType identityType, Set<Role> roles) {
        return new CurrentUser(identityId, true, identityType, roles);
    }

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }

    public boolean isGuest() {
        return authenticated && identityType == IdentityType.GUEST;
    }

    /**
     * The reference other code uses to point at this identity.
     */
    public IdentityReference reference() {
        if (!authenticated) {
            throw new IllegalStateException("An anonymous user has no identity reference");
        }
        return new IdentityReference(identityId, identityType);
    }
}
