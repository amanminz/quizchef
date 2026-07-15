package io.quizchef.quiz.api;

import io.quizchef.quiz.application.QuizLocalizationCommand;
import io.quizchef.quiz.domain.QuizLocalization;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * One language's quiz content, in requests and responses alike.
 */
public record QuizLocalizationDto(
        @Schema(example = "en") @NotBlank String languageCode,
        @Schema(example = "Bible Quiz") @NotBlank @Size(max = 200) String title,
        @Schema(example = "Sunday Youth Fellowship") @Size(max = 2000) String description
) {

    QuizLocalizationCommand toCommand() {
        return new QuizLocalizationCommand(languageCode, title, description);
    }

    static QuizLocalizationDto from(QuizLocalization localization) {
        return new QuizLocalizationDto(
                localization.languageCode().value(),
                localization.title(),
                localization.description());
    }
}
