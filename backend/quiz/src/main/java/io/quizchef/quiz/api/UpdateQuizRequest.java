package io.quizchef.quiz.api;

import io.quizchef.quiz.application.QuizLocalizationCommand;
import io.quizchef.quiz.application.UpdateQuizCommand;
import io.quizchef.quiz.domain.QuizVisibility;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * Updates a quiz. Omitted fields stay unchanged; a provided localization
 * list replaces the full set of translations. The version must be the one
 * last read — a stale version is rejected with 409 so concurrent authors
 * never silently overwrite each other.
 */
public record UpdateQuizRequest(
        @Schema(example = "0", description = "The version returned by the last read of this quiz")
        @NotNull Long version,
        QuizVisibility visibility,
        @Valid QuizSettingsDto settings,
        @Valid List<QuizLocalizationDto> localizations
) {

    UpdateQuizCommand toCommand(UUID quizId) {
        List<QuizLocalizationCommand> localizationCommands = localizations == null
                ? null
                : localizations.stream().map(QuizLocalizationDto::toCommand).toList();
        return new UpdateQuizCommand(
                quizId,
                version,
                visibility,
                settings == null ? null : settings.toCommand(),
                localizationCommands);
    }
}
