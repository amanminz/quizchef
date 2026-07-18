package io.quizchef.platform.security.configuration;

import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNoException;

import io.quizchef.identity.infrastructure.jwt.JwtProperties;
import io.quizchef.platform.security.logging.SecurityEventLogger;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class JwtSecretSafetyCheckTest {

    private final SecurityEventLogger securityEventLogger = new SecurityEventLogger();

    private JwtProperties propertiesWith(String secret) {
        return new JwtProperties("quizchef", secret, Duration.ofMinutes(15), null);
    }

    @Test
    void failsFastOnTheLocalPlaceholderSecret() {
        JwtSecretSafetyCheck check = new JwtSecretSafetyCheck(
                propertiesWith("quizchef-local-development-secret-0001"), securityEventLogger);

        assertThatIllegalStateException().isThrownBy(check::verifySecretIsNotAKnownPlaceholder);
    }

    @Test
    void failsFastOnTheTestPlaceholderSecret() {
        JwtSecretSafetyCheck check = new JwtSecretSafetyCheck(
                propertiesWith("quizchef-test-only-signing-secret-0001"), securityEventLogger);

        assertThatIllegalStateException().isThrownBy(check::verifySecretIsNotAKnownPlaceholder);
    }

    @Test
    void passesForARealProductionSecret() {
        JwtSecretSafetyCheck check = new JwtSecretSafetyCheck(
                propertiesWith("a-genuinely-random-production-secret-value-0000"), securityEventLogger);

        assertThatNoException().isThrownBy(check::verifySecretIsNotAKnownPlaceholder);
    }
}
