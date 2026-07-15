package io.quizchef.quiz.application;

import io.quizchef.quiz.domain.LanguageCode;
import io.quizchef.quiz.domain.Quiz;
import io.quizchef.quiz.domain.QuizLocalization;
import io.quizchef.quiz.domain.QuizQuestion;
import io.quizchef.quiz.domain.QuizSettings;
import io.quizchef.quiz.domain.QuizState;
import io.quizchef.quiz.domain.QuizVisibility;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The application layer's read model of a quiz: metadata, settings,
 * lifecycle, localizations, and ordered question ids. Questions are
 * separate aggregates and are never embedded.
 */
public record QuizView(
        UUID id,
        UUID ownerIdentityId,
        LanguageCode defaultLanguage,
        QuizState state,
        QuizVisibility visibility,
        long version,
        QuizSettings settings,
        List<QuizLocalization> localizations,
        List<UUID> questionIds,
        Instant createdAt,
        Instant updatedAt
) {

    public static QuizView of(Quiz quiz) {
        return new QuizView(
                quiz.getId(),
                quiz.getOwnerIdentity().identityId(),
                quiz.getDefaultLanguage(),
                quiz.getState(),
                quiz.getVisibility(),
                quiz.getVersion(),
                quiz.getSettings(),
                quiz.localizations(),
                quiz.questions().stream().map(QuizQuestion::questionId).toList(),
                quiz.getCreatedAt(),
                quiz.getUpdatedAt());
    }
}
