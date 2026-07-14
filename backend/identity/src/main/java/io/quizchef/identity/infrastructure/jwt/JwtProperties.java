package io.quizchef.identity.infrastructure.jwt;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT signing and lifetime configuration.
 *
 * <p>The secret arrives through the {@code JWT_SECRET} environment variable
 * and must be at least 256 bits (32 bytes) — the minimum for HMAC-SHA256.
 *
 * @param issuer         value of the {@code iss} claim, asserted on validation
 * @param secret         HMAC-SHA256 signing secret
 * @param accessTokenTtl lifetime of issued access tokens
 * @param audience       optional {@code aud} claim; when configured it is set
 *                       on issued tokens and asserted on validation
 */
@ConfigurationProperties(prefix = "quizchef.security.jwt")
public record JwtProperties(String issuer, String secret, Duration accessTokenTtl, String audience) {

    private static final int MINIMUM_SECRET_BYTES = 32;

    public JwtProperties {
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException("quizchef.security.jwt.issuer must not be blank");
        }
        if (audience != null && audience.isBlank()) {
            audience = null;
        }
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MINIMUM_SECRET_BYTES) {
            throw new IllegalArgumentException(
                    "quizchef.security.jwt.secret must be at least %d bytes".formatted(MINIMUM_SECRET_BYTES));
        }
        if (accessTokenTtl == null || accessTokenTtl.isNegative() || accessTokenTtl.isZero()) {
            throw new IllegalArgumentException("quizchef.security.jwt.access-token-ttl must be positive");
        }
    }

    public boolean hasAudience() {
        return audience != null;
    }
}
