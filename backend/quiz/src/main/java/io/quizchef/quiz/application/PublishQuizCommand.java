package io.quizchef.quiz.application;

import java.util.UUID;

/**
 * Publishes a draft quiz.
 */
public record PublishQuizCommand(UUID quizId) {
}
