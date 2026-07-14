package io.quizchef.identity.infrastructure.jwt;

import io.quizchef.identity.domain.IdentityType;
import io.quizchef.identity.domain.Role;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * The verified content of an access token.
 *
 * <p>{@code sessionId} binds the token to the IdentitySession it was issued
 * for; the request is only authenticated while that session is active.
 */
public record IdentityToken(
        UUID identityId,
        UUID sessionId,
        IdentityType identityType,
        Set<Role> roles,
        Instant expiresAt
) {

    public IdentityToken {
        roles = Set.copyOf(roles);
    }
}
