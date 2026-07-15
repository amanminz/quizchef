package io.quizchef.quiz.application;

import io.quizchef.quiz.domain.LanguageCode;
import io.quizchef.quiz.domain.QuizLocalization;

/**
 * One language's quiz content, as requested by the caller.
 */
public record QuizLocalizationCommand(
        String languageCode,
        String title,
        String description
) {

    QuizLocalization toLocalization() {
        return new QuizLocalization(LanguageCode.of(languageCode), title, description);
    }
}
