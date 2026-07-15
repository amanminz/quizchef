package io.quizchef.quiz.api;

import io.quizchef.quiz.application.QuizSettingsCommand;
import io.quizchef.quiz.domain.QuizSettings;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Hosting behavior of a quiz, in requests and responses alike.
 */
public record QuizSettingsDto(
        @NotNull Boolean randomizeQuestionOrder,
        @NotNull Boolean randomizeOptionOrder,
        @Schema(example = "30") @NotNull @Min(5) @Max(300) Integer questionTimeLimitSeconds,
        @NotNull Boolean showLeaderboardAfterQuestion,
        @NotNull Boolean showExplanationAfterQuestion
) {

    QuizSettingsCommand toCommand() {
        return new QuizSettingsCommand(randomizeQuestionOrder, randomizeOptionOrder,
                questionTimeLimitSeconds, showLeaderboardAfterQuestion, showExplanationAfterQuestion);
    }

    static QuizSettingsDto from(QuizSettings settings) {
        return new QuizSettingsDto(
                settings.randomizeQuestionOrder(),
                settings.randomizeOptionOrder(),
                settings.questionTimeLimitSeconds(),
                settings.showLeaderboardAfterQuestion(),
                settings.showExplanationAfterQuestion());
    }
}
