package io.quizchef.quiz.api;

import io.quizchef.quiz.application.OptionTextCommand;
import io.quizchef.quiz.domain.OptionLocalization;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * One option's text in the language of the surrounding localization.
 */
public record OptionTextDto(
        @NotNull UUID optionId,
        @NotBlank @Size(max = 500) String text
) {

    OptionTextCommand toCommand() {
        return new OptionTextCommand(optionId, text);
    }

    static OptionTextDto from(OptionLocalization localization) {
        return new OptionTextDto(localization.optionId(), localization.text());
    }
}
