package io.quizchef.quiz.application;

import java.util.UUID;

/**
 * Archives a published question. Archived questions are retained and keep
 * working inside existing published quizzes.
 */
public record ArchiveQuestionCommand(UUID questionId) {
}
