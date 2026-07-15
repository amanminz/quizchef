package io.quizchef.quiz.application;

import java.util.UUID;

/**
 * Archives a published quiz. Archived quizzes are retained, never deleted.
 */
public record ArchiveQuizCommand(UUID quizId) {
}
