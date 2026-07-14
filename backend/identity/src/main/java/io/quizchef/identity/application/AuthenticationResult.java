package io.quizchef.identity.application;

import io.quizchef.identity.domain.Role;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Outcome of successful authentication (RFC-002): everything the API layer
 * needs to answer a login, nothing more. The controller stays a thin mapper.
 *
 * @param refreshToken always null until the refresh-token PR
 */
public record AuthenticationResult(
        UUID identityId,
        String displayName,
        String token,
        Instant expiresAt,
        String refreshToken,
        Set<Role> authorities
) {

    public AuthenticationResult {
        authorities = Set.copyOf(authorities);
    }

    /**
     * The token is a credential; it must never reach logs.
     */
    @Override
    public String toString() {
        return "AuthenticationResult[identityId=%s, token=<redacted>, expiresAt=%s, authorities=%s]"
                .formatted(identityId, expiresAt, authorities);
    }
}
