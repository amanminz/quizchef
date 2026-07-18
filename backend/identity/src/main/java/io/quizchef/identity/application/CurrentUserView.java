package io.quizchef.identity.application;

import io.quizchef.identity.domain.IdentityType;
import io.quizchef.identity.domain.Permission;
import io.quizchef.identity.domain.Role;
import java.util.Set;
import java.util.UUID;

/**
 * What an authenticated caller sees about themselves: their identity,
 * roles, the permissions derived from them, and — for a registered user —
 * their own profile basics. The caller's own data is not "PII leakage";
 * it is theirs (USER_PROFILE_READ gates the read). Null profile fields
 * for identities without a profile (guests).
 */
public record CurrentUserView(
        UUID identityId,
        IdentityType identityType,
        Set<Role> roles,
        Set<Permission> permissions,
        String displayName,
        String email
) {

    public CurrentUserView {
        roles = Set.copyOf(roles);
        permissions = Set.copyOf(permissions);
    }
}
