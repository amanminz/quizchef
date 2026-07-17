package io.quizchef.quiz.application;

import java.util.UUID;

/**
 * Attaches an owned, published question to a quiz's composition.
 */
public record AddQuestionToQuizCommand(UUID quizId, UUID questionId) {
}
