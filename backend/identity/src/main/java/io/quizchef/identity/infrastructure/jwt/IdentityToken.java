package io.quizchef.identity.infrastructure.jwt;

import io.quizchef.identity.domain.IdentityType;
import io.quizchef.identity.domain.Role;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * The verified content of an access token.
 */
public record IdentityToken(
        UUID identityId,
        IdentityType identityType,
        Set<Role> roles,
        Instant expiresAt
) {

    public IdentityToken {
        roles = Set.copyOf(roles);
    }
}
