package io.quizchef.quiz.application;

import java.util.UUID;

/**
 * Detaches a question from a quiz's composition. Draft quizzes only.
 */
public record RemoveQuestionFromQuizCommand(UUID quizId, UUID questionId) {
}
