package io.quizchef.identity.application;

import io.quizchef.identity.domain.IdentityType;
import io.quizchef.identity.domain.Permission;
import io.quizchef.identity.domain.Role;
import java.util.Set;
import java.util.UUID;

/**
 * What an authenticated caller sees about themselves: their identity,
 * roles, and the permissions derived from them. No PII.
 */
public record CurrentUserView(
        UUID identityId,
        IdentityType identityType,
        Set<Role> roles,
        Set<Permission> permissions
) {

    public CurrentUserView {
        roles = Set.copyOf(roles);
        permissions = Set.copyOf(permissions);
    }
}
