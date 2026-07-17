package io.quizchef.quiz.application;

import io.quizchef.quiz.domain.Difficulty;
import io.quizchef.quiz.domain.LanguageCode;
import io.quizchef.quiz.domain.QuestionState;
import java.util.List;
import java.util.UUID;

/**
 * Filters for browsing the caller's own question library — every field is
 * optional; an absent filter matches everything. {@code tagIds} matches a
 * question tagged with at least one of the given tags. {@code search}
 * matches the title or prompt of any localization, case-insensitively.
 */
public record QuestionSearchQuery(
        QuestionState state,
        Difficulty difficulty,
        LanguageCode language,
        List<UUID> tagIds,
        String search
) {
}
