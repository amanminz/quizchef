package io.quizchef.quiz.domain;

import jakarta.persistence.Embeddable;

/**
 * How a quiz behaves when hosted. New settings join as new components —
 * no redesign required.
 */
@Embeddable
public record QuizSettings(
        boolean randomizeQuestionOrder,
        boolean randomizeOptionOrder,
        int questionTimeLimitSeconds,
        boolean showLeaderboardAfterQuestion,
        boolean showExplanationAfterQuestion
) {

    static final int MIN_TIME_LIMIT_SECONDS = 5;
    static final int MAX_TIME_LIMIT_SECONDS = 300;

    public QuizSettings {
        if (questionTimeLimitSeconds < MIN_TIME_LIMIT_SECONDS
                || questionTimeLimitSeconds > MAX_TIME_LIMIT_SECONDS) {
            throw new IllegalArgumentException(
                    "questionTimeLimitSeconds must be between %d and %d"
                            .formatted(MIN_TIME_LIMIT_SECONDS, MAX_TIME_LIMIT_SECONDS));
        }
    }

    /**
     * The Kahoot-style defaults: fixed order, 30 seconds per question,
     * leaderboard and explanation after every question.
     */
    public static QuizSettings defaults() {
        return new QuizSettings(false, false, 30, true, true);
    }
}
