package io.quizchef.quiz.api;

import io.quizchef.quiz.application.ReorderQuizQuestionsCommand;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

/**
 * The quiz's questions in their new order. Must name exactly the quiz's
 * current questions, each once — the aggregate rejects anything else.
 */
public record ReorderQuestionsRequest(
        @NotEmpty List<UUID> questionIds
) {

    ReorderQuizQuestionsCommand toCommand(UUID quizId) {
        return new ReorderQuizQuestionsCommand(quizId, questionIds);
    }
}
