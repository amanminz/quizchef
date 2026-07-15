package io.quizchef.quiz.application;

import java.util.UUID;

/**
 * Publishes a draft question, freezing it for reuse.
 */
public record PublishQuestionCommand(UUID questionId) {
}
