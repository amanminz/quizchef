package io.quizchef.identity.infrastructure.jwt;

import java.time.Instant;

/**
 * A freshly issued access token together with its expiry.
 */
public record IssuedToken(String token, Instant expiresAt) {
}
