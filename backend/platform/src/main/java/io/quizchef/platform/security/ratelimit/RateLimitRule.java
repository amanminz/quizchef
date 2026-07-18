package io.quizchef.platform.security.ratelimit;

import java.time.Duration;

/**
 * How many requests a bucket allows within a window, before it starts
 * refusing and refilling gradually.
 */
public record RateLimitRule(int capacity, Duration window) {

    public RateLimitRule {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        if (window == null || window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("window must be positive");
        }
    }
}
