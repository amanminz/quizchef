package io.quizchef.identity.api;

import io.quizchef.identity.application.AuthenticationResult;
import io.quizchef.identity.domain.Role;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Login response: the session-bound access token and its expiry.
 * No refresh token yet — that field joins in the refresh-token PR.
 */
public record LoginResponse(
        UUID identityId,
        String displayName,
        String token,
        Instant expiresAt,
        Set<Role> authorities
) {

    static LoginResponse from(AuthenticationResult result) {
        return new LoginResponse(
                result.identityId(),
                result.displayName(),
                result.token(),
                result.expiresAt(),
                result.authorities());
    }
}
