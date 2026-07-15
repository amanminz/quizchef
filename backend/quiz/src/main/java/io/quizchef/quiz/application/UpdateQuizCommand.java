package io.quizchef.quiz.application;

import io.quizchef.quiz.domain.QuizVisibility;
import java.util.List;
import java.util.UUID;

/**
 * Updates a quiz. Null fields stay unchanged; a non-null localization list
 * replaces the full set of translations. The version must match the quiz's
 * current version — a stale version means someone else saved in between.
 */
public record UpdateQuizCommand(
        UUID quizId,
        long version,
        QuizVisibility visibility,
        QuizSettingsCommand settings,
        List<QuizLocalizationCommand> localizations
) {
}
