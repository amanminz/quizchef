package io.quizchef.quiz.application;

import io.quizchef.quiz.domain.QuizVisibility;

/**
 * Creates a draft quiz. The owner is never part of the command — it is
 * always the authenticated caller.
 *
 * @param visibility optional; PRIVATE when null
 * @param settings   optional; defaults when null
 */
public record CreateQuizCommand(
        String defaultLanguage,
        QuizVisibility visibility,
        QuizLocalizationCommand localization,
        QuizSettingsCommand settings
) {
}
