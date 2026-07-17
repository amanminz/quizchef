package io.quizchef.quiz.application;

import java.util.List;
import java.util.UUID;

/**
 * Repositions a quiz's questions. Draft quizzes only.
 */
public record ReorderQuizQuestionsCommand(UUID quizId, List<UUID> orderedQuestionIds) {
}
