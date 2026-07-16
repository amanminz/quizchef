package io.quizchef.session.domain;

import jakarta.persistence.Embeddable;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * When the current question opened and when it closes. A pure model — no
 * scheduling, no countdown, no expiry checking. The progression flow (a
 * later PR) populates it; here it exists only so the shape is settled.
 *
 * <p>Stored as seconds for schema consistency; exposed as {@link Duration}.
 */
@Embeddable
public record QuestionTimer(
        Instant startedAt,
        int durationSeconds,
        Instant endsAt
) {

    public QuestionTimer {
        Objects.requireNonNull(startedAt, "startedAt must not be null");
        Objects.requireNonNull(endsAt, "endsAt must not be null");
        if (durationSeconds <= 0) {
            throw new IllegalArgumentException("durationSeconds must be positive");
        }
        if (!endsAt.equals(startedAt.plusSeconds(durationSeconds))) {
            throw new IllegalArgumentException("endsAt must equal startedAt plus the duration");
        }
    }

    public static QuestionTimer startingAt(Instant startedAt, Duration duration) {
        int seconds = Math.toIntExact(duration.toSeconds());
        return new QuestionTimer(startedAt, seconds, startedAt.plusSeconds(seconds));
    }

    public Duration duration() {
        return Duration.ofSeconds(durationSeconds);
    }
}
