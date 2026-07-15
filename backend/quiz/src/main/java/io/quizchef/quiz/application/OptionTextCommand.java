package io.quizchef.quiz.application;

import io.quizchef.quiz.domain.LanguageCode;
import io.quizchef.quiz.domain.OptionLocalization;
import java.util.UUID;

/**
 * One option's text in the language of the surrounding
 * {@link QuestionContentCommand}.
 */
public record OptionTextCommand(
        UUID optionId,
        String text
) {

    OptionLocalization toLocalization(LanguageCode languageCode) {
        return new OptionLocalization(optionId, languageCode, text);
    }
}
