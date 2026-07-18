package io.quizchef.platform.security.configuration;

import io.quizchef.identity.infrastructure.jwt.JwtProperties;
import io.quizchef.platform.security.logging.SecurityEventLogger;
import jakarta.annotation.PostConstruct;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Fails application startup, in {@code prod} only, if {@code JWT_SECRET}
 * is one of the checked-in local/test placeholder values (Phase 3 PR #3 /
 * RFC-011) — "secure by default": an accidental prod deploy with a dev
 * secret is refused rather than silently running insecurely.
 */
@Component
@Profile("prod")
public class JwtSecretSafetyCheck {

    private static final Set<String> KNOWN_PLACEHOLDER_SECRETS = Set.of(
            "quizchef-local-development-secret-0001",
            "quizchef-test-only-signing-secret-0001");

    private final JwtProperties jwtProperties;
    private final SecurityEventLogger securityEventLogger;

    public JwtSecretSafetyCheck(JwtProperties jwtProperties, SecurityEventLogger securityEventLogger) {
        this.jwtProperties = jwtProperties;
        this.securityEventLogger = securityEventLogger;
    }

    @PostConstruct
    void verifySecretIsNotAKnownPlaceholder() {
        if (KNOWN_PLACEHOLDER_SECRETS.contains(jwtProperties.secret())) {
            securityEventLogger.jwtSecretValidationFailed();
            throw new IllegalStateException(
                    "JWT_SECRET is set to a known local/test placeholder value — refusing to start in prod");
        }
    }
}
