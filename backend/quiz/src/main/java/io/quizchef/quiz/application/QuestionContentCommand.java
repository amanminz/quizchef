package io.quizchef.quiz.application;

import io.quizchef.quiz.domain.LanguageCode;
import io.quizchef.quiz.domain.OptionLocalization;
import io.quizchef.quiz.domain.QuestionLocalization;
import java.util.List;

/**
 * One language's question content — text plus the texts of every option.
 */
public record QuestionContentCommand(
        String languageCode,
        String title,
        String prompt,
        String explanation,
        List<OptionTextCommand> optionTexts
) {

    QuestionLocalization toLocalization() {
        return new QuestionLocalization(LanguageCode.of(languageCode), title, prompt, explanation);
    }

    List<OptionLocalization> toOptionTexts() {
        LanguageCode language = LanguageCode.of(languageCode);
        return optionTexts.stream()
                .map(text -> text.toLocalization(language))
                .toList();
    }
}
