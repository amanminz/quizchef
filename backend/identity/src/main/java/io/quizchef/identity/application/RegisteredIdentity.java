package io.quizchef.identity.application;

import java.time.Instant;
import java.util.UUID;

/**
 * Outcome of a successful registration, handed to the API layer.
 * Entities never leave the module.
 */
public record RegisteredIdentity(
        UUID identityId,
        String displayName,
        String email,
        Instant createdAt
) {
}
