package io.quizchef.quiz.api;

import io.quizchef.quiz.application.QuestionContentCommand;
import io.quizchef.quiz.application.QuestionView;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * One language's question content: text plus the texts of every option —
 * a translation is whole or absent.
 */
public record QuestionLocalizationDto(
        @Schema(example = "en") @NotBlank String languageCode,
        @Schema(example = "Exodus leader") @NotBlank @Size(max = 200) String title,
        @Schema(example = "Who led Israel out of Egypt?") @NotBlank @Size(max = 4000) String prompt,
        @Schema(example = "See Exodus 3.") @Size(max = 4000) String explanation,
        @NotNull @Valid List<OptionTextDto> optionTexts
) {

    QuestionContentCommand toCommand() {
        return new QuestionContentCommand(languageCode, title, prompt, explanation,
                optionTexts.stream().map(OptionTextDto::toCommand).toList());
    }

    static QuestionLocalizationDto from(QuestionView.LocalizationView view) {
        return new QuestionLocalizationDto(
                view.languageCode().value(),
                view.title(),
                view.prompt(),
                view.explanation(),
                view.optionTexts().stream().map(OptionTextDto::from).toList());
    }
}
