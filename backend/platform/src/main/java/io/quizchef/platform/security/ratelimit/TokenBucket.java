package io.quizchef.platform.security.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * A single caller's bucket: refills continuously toward {@code capacity}
 * over {@code window}, and each request consumes one token. In-memory and
 * per-process — this is a single-instance, church-scale deployment
 * (RFC-008), so there is no shared/distributed state to coordinate.
 */
final class TokenBucket {

    private final int capacity;
    private final Duration window;
    private final Clock clock;

    private double availableTokens;
    private Instant lastRefill;

    TokenBucket(int capacity, Duration window, Clock clock) {
        this.capacity = capacity;
        this.window = window;
        this.clock = clock;
        this.availableTokens = capacity;
        this.lastRefill = clock.instant();
    }

    synchronized boolean tryConsume() {
        refill();
        if (availableTokens >= 1.0) {
            availableTokens -= 1.0;
            return true;
        }
        return false;
    }

    synchronized int remaining() {
        refill();
        return (int) Math.floor(availableTokens);
    }

    /**
     * Seconds until one token is available — what a refused caller should
     * actually wait, and therefore what belongs in {@code Retry-After}.
     *
     * <p>Distinct from {@link #secondsUntilReset()}, which is the window
     * this bucket refills over and what {@code X-RateLimit-Reset}
     * conventionally means. The two used to be the same value, which told a
     * caller refused by a 150-per-minute bucket to wait a full minute when
     * the next token was 0.4 seconds away — conservative, but wrong, and at
     * a venue it is the difference between a phone retrying promptly and a
     * participant giving up.
     *
     * <p>Rounded up, so the answer is never optimistic: waiting the
     * returned number of seconds always finds a token.
     */
    synchronized long secondsUntilNextToken() {
        refill();
        if (availableTokens >= 1.0) {
            return 0;
        }
        double secondsPerToken = (window.toNanos() / 1_000_000_000.0) / capacity;
        return (long) Math.ceil((1.0 - availableTokens) * secondsPerToken);
    }

    /** Seconds until the bucket is back to full capacity. */
    synchronized long secondsUntilReset() {
        refill();
        if (availableTokens >= capacity) {
            return 0;
        }
        double missingTokens = capacity - availableTokens;
        double secondsPerToken = (window.toNanos() / 1_000_000_000.0) / capacity;
        return Math.round(missingTokens * secondsPerToken);
    }

    int capacity() {
        return capacity;
    }

    private void refill() {
        Instant now = clock.instant();
        double elapsedSeconds = Duration.between(lastRefill, now).toNanos() / 1_000_000_000.0;
        if (elapsedSeconds <= 0) {
            return;
        }
        double refillRatePerSecond = capacity / (window.toNanos() / 1_000_000_000.0);
        availableTokens = Math.min(capacity, availableTokens + elapsedSeconds * refillRatePerSecond);
        lastRefill = now;
    }
}
