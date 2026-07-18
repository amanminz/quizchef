package io.quizchef.platform.security.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Structured logging for security occurrences that originate inside
 * {@code platform} itself (Phase 3 PR #3 / RFC-011) — operational signal
 * ("why is this user getting blocked", "are we seeing join-code
 * brute-forcing"), never audit history. Occurrences detected in other
 * modules (an invalid JWT in {@code security}, a malformed STOMP
 * destination in {@code websocket}) log inline in their own existing code
 * instead of depending on this class, so no business module ever needs a
 * dependency on {@code platform} just to log a security event — see
 * RFC-011 for the full rationale.
 */
@Component
public class SecurityEventLogger {

    private static final Logger log = LoggerFactory.getLogger(SecurityEventLogger.class);

    public void rateLimitTriggered(String method, String route, String subject) {
        log.warn("security.rate_limit_triggered method={} route={} subject={}", method, route, subject);
    }

    public void oversizedRequest(long contentLength, long maxAllowedBytes) {
        log.warn("security.oversized_request contentLength={} maxAllowedBytes={}",
                contentLength, maxAllowedBytes);
    }

    public void jwtSecretValidationFailed() {
        log.error("security.jwt_secret_validation_failed reason=placeholder_secret_in_prod");
    }
}
