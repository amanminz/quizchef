package io.quizchef.quiz.api;

import io.quizchef.quiz.application.CreateQuizCommand;
import io.quizchef.quiz.domain.QuizVisibility;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Creates a draft quiz. The caller becomes the owner — ownership is never
 * part of the request.
 */
public record CreateQuizRequest(
        @Schema(example = "en", description = "BCP-47 tag; the localization must be in this language")
        @NotBlank @Size(max = 35) String defaultLanguage,
        @Schema(description = "PRIVATE when omitted") QuizVisibility visibility,
        @NotNull @Valid QuizLocalizationDto localization,
        @Schema(description = "Defaults applied when omitted") @Valid QuizSettingsDto settings
) {

    CreateQuizCommand toCommand() {
        return new CreateQuizCommand(
                defaultLanguage,
                visibility,
                localization.toCommand(),
                settings == null ? null : settings.toCommand());
    }
}
