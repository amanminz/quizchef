package io.quizchef.session.domain;

import io.quizchef.quiz.domain.Difficulty;
import java.time.Duration;
import java.util.Objects;

/**
 * Computes the points an answer earns — server-authoritative (ADR-006) and
 * framework-independent. A pure function of primitives: correctness, how
 * fast the answer came, how long the question was open, and the difficulty,
 * against a {@link ScoringPolicy}. No entities, no clock, no I/O — so it is
 * fully deterministic and trivially tested.
 *
 * <p>Formula (classic policy): an incorrect answer scores 0; a correct one
 * scores {@code round((base + maxSpeedBonus × remainingFraction) ×
 * difficultyMultiplier)}, where {@code remainingFraction} is the share of
 * the question's time still left when the answer arrived, clamped to
 * {@code [0, 1]}. Faster answers keep more of the speed bonus. Never
 * negative.
 */
public class ScoringService {

    public int award(boolean correct, Duration responseTime, Duration questionDuration,
                     Difficulty difficulty, ScoringPolicy policy) {
        Objects.requireNonNull(responseTime, "responseTime must not be null");
        Objects.requireNonNull(questionDuration, "questionDuration must not be null");
        Objects.requireNonNull(difficulty, "difficulty must not be null");
        Objects.requireNonNull(policy, "policy must not be null");
        if (!correct) {
            return 0;
        }
        double remainingFraction = remainingFraction(responseTime, questionDuration);
        double raw = (policy.basePoints() + policy.maxSpeedBonus() * remainingFraction)
                * policy.multiplierFor(difficulty);
        return Math.max(0, Math.toIntExact(Math.round(raw)));
    }

    private static double remainingFraction(Duration responseTime, Duration questionDuration) {
        long durationMillis = questionDuration.toMillis();
        if (durationMillis <= 0) {
            return 0.0;
        }
        double fraction = 1.0 - (double) responseTime.toMillis() / durationMillis;
        return Math.max(0.0, Math.min(1.0, fraction));
    }
}
