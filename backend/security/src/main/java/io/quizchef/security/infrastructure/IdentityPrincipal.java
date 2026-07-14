package io.quizchef.security.infrastructure;

import io.quizchef.identity.domain.IdentityType;
import io.quizchef.identity.domain.Role;
import java.util.Set;
import java.util.UUID;

/**
 * The principal the future JWT authentication filter places into Spring
 * Security's context after verifying a token.
 *
 * <p>Only the security module knows this type; business code sees the
 * framework-independent CurrentUser produced from it.
 */
public record IdentityPrincipal(
        UUID identityId,
        IdentityType identityType,
        Set<Role> roles
) {

    public IdentityPrincipal {
        roles = Set.copyOf(roles);
    }
}
