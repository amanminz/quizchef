package io.quizchef.quiz.application;

import io.quizchef.quiz.domain.QuizSettings;

/**
 * Requested quiz settings; range rules are enforced by the domain value
 * object on conversion.
 */
public record QuizSettingsCommand(
        boolean randomizeQuestionOrder,
        boolean randomizeOptionOrder,
        int questionTimeLimitSeconds,
        boolean showLeaderboardAfterQuestion,
        boolean showExplanationAfterQuestion
) {

    QuizSettings toSettings() {
        return new QuizSettings(randomizeQuestionOrder, randomizeOptionOrder,
                questionTimeLimitSeconds, showLeaderboardAfterQuestion, showExplanationAfterQuestion);
    }
}
