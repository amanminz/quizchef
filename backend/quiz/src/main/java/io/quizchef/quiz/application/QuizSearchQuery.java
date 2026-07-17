package io.quizchef.quiz.application;

import io.quizchef.quiz.domain.QuizState;

/**
 * Filters for "My Quizzes" — every field is optional; an absent filter
 * matches everything. {@code search} matches the title of any localization,
 * case-insensitively.
 */
public record QuizSearchQuery(QuizState state, String search) {
}
