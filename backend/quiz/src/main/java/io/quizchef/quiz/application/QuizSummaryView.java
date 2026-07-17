package io.quizchef.quiz.application;

import io.quizchef.quiz.domain.LanguageCode;
import io.quizchef.quiz.domain.Quiz;
import io.quizchef.quiz.domain.QuizState;
import java.time.Instant;
import java.util.UUID;

/**
 * A quiz as "My Quizzes" presents it — a lean preview for a list/card, not
 * the full editable representation ({@link QuizView}). Deliberately leaner:
 * no settings, no localization list, no question ids — a list of many
 * quizzes should not carry what only the editor needs.
 */
public record QuizSummaryView(
        UUID id,
        String title,
        String description,
        QuizState state,
        LanguageCode defaultLanguage,
        int questionCount,
        long version,
        Instant updatedAt
) {

    static QuizSummaryView of(Quiz quiz) {
        return new QuizSummaryView(
                quiz.getId(),
                quiz.defaultLocalization().title(),
                quiz.defaultLocalization().description(),
                quiz.getState(),
                quiz.getDefaultLanguage(),
                quiz.questions().size(),
                quiz.getVersion(),
                quiz.getUpdatedAt());
    }
}
