package io.quizchef.session.domain;

import io.quizchef.quiz.domain.Difficulty;

/**
 * The tunable constants of a scoring scheme: the base points a correct
 * answer is worth, the extra points a maximally fast answer earns, and the
 * multiplier each difficulty applies.
 *
 * <p>Isolating these here — rather than baking them into {@link
 * ScoringService} — means "Classic", "Kids", "Practice", or "Tournament"
 * scoring is a different policy, not different engine code. A future streak
 * bonus is a new field here, not a new branch in orchestration.
 */
public record ScoringPolicy(
        int basePoints,
        int maxSpeedBonus,
        double easyMultiplier,
        double mediumMultiplier,
        double hardMultiplier
) {

    public ScoringPolicy {
        if (basePoints < 0 || maxSpeedBonus < 0) {
            throw new IllegalArgumentException("points must not be negative");
        }
    }

    /**
     * The default Kahoot-style scheme: 500 base, up to 500 speed bonus (so a
     * flat-out-fast correct answer approaches 1000 before difficulty), scaled
     * by difficulty ×1.0 / ×1.25 / ×1.5.
     */
    public static ScoringPolicy classic() {
        return new ScoringPolicy(500, 500, 1.0, 1.25, 1.5);
    }

    public double multiplierFor(Difficulty difficulty) {
        return switch (difficulty) {
            case EASY -> easyMultiplier;
            case MEDIUM -> mediumMultiplier;
            case HARD -> hardMultiplier;
        };
    }
}
