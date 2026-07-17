package io.quizchef.quiz.api;

import io.quizchef.quiz.application.AddQuestionToQuizCommand;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Attaches one of the caller's own, published questions to a quiz.
 */
public record AddQuestionRequest(
        @NotNull UUID questionId
) {

    AddQuestionToQuizCommand toCommand(UUID quizId) {
        return new AddQuestionToQuizCommand(quizId, questionId);
    }
}
