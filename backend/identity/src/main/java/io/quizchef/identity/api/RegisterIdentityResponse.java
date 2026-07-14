package io.quizchef.identity.api;

import io.quizchef.identity.application.RegisteredIdentity;
import java.time.Instant;
import java.util.UUID;

/**
 * Registration response. Never contains the password or its hash,
 * and no token — registration does not log the user in.
 */
public record RegisterIdentityResponse(
        UUID identityId,
        String displayName,
        String email,
        Instant createdAt
) {

    static RegisterIdentityResponse from(RegisteredIdentity registered) {
        return new RegisterIdentityResponse(
                registered.identityId(),
                registered.displayName(),
                registered.email(),
                registered.createdAt());
    }
}
