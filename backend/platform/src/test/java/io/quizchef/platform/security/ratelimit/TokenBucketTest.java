package io.quizchef.platform.security.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class TokenBucketTest {

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-07-18T10:00:00Z"));
    private final Clock clock = new Clock() {
        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    };

    @Test
    void allowsUpToCapacityThenBlocks() {
        TokenBucket bucket = new TokenBucket(3, Duration.ofMinutes(1), clock);

        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isFalse();
    }

    @Test
    void refillsGraduallyOverTheWindow() {
        TokenBucket bucket = new TokenBucket(2, Duration.ofMinutes(1), clock);
        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isFalse();

        now.set(now.get().plus(Duration.ofSeconds(30)));

        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isFalse();
    }

    @Test
    void fullyRecoversAfterTheWholeWindowElapses() {
        TokenBucket bucket = new TokenBucket(2, Duration.ofMinutes(1), clock);
        bucket.tryConsume();
        bucket.tryConsume();
        assertThat(bucket.tryConsume()).isFalse();

        now.set(now.get().plus(Duration.ofMinutes(1)));

        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isFalse();
    }

    @Test
    void remainingReflectsConsumedTokens() {
        TokenBucket bucket = new TokenBucket(5, Duration.ofMinutes(1), clock);
        assertThat(bucket.remaining()).isEqualTo(5);

        bucket.tryConsume();

        assertThat(bucket.remaining()).isEqualTo(4);
    }

    @Test
    void secondsUntilResetIsZeroWhenFull() {
        TokenBucket bucket = new TokenBucket(5, Duration.ofMinutes(1), clock);

        assertThat(bucket.secondsUntilReset()).isZero();
    }

    @Test
    void secondsUntilResetCountsDownAfterConsumption() {
        TokenBucket bucket = new TokenBucket(1, Duration.ofMinutes(1), clock);
        bucket.tryConsume();

        assertThat(bucket.secondsUntilReset()).isEqualTo(60);
    }

    @Test
    void capacityIsExposed() {
        TokenBucket bucket = new TokenBucket(7, Duration.ofSeconds(30), clock);

        assertThat(bucket.capacity()).isEqualTo(7);
    }
}
