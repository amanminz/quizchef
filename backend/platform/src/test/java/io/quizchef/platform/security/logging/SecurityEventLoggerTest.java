package io.quizchef.platform.security.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SecurityEventLoggerTest {

    private final SecurityEventLogger logger = new SecurityEventLogger();

    @Test
    void logsRateLimitTriggered() {
        try (LogCapture capture = new LogCapture(SecurityEventLogger.class)) {
            logger.rateLimitTriggered("POST", "/api/v1/auth/login", "ip:127.0.0.1");

            assertThat(capture.messages()).anySatisfy(message -> {
                assertThat(message).contains("security.rate_limit_triggered");
                assertThat(message).contains("/api/v1/auth/login");
            });
        }
    }

    @Test
    void logsOversizedRequest() {
        try (LogCapture capture = new LogCapture(SecurityEventLogger.class)) {
            logger.oversizedRequest(1_000_000L, 262_144L);

            assertThat(capture.messages()).anyMatch(m -> m.contains("security.oversized_request"));
        }
    }

    @Test
    void logsJwtSecretValidationFailed() {
        try (LogCapture capture = new LogCapture(SecurityEventLogger.class)) {
            logger.jwtSecretValidationFailed();

            assertThat(capture.messages()).anyMatch(m -> m.contains("security.jwt_secret_validation_failed"));
        }
    }
}
