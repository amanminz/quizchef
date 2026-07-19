package io.quizchef.session.api;

import io.quizchef.session.application.AnswerProgressView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * The host's live answer progress for the question in play — counts only,
 * never who has or has not answered.
 */
public record AnswerProgressResponse(
        UUID sessionId,
        UUID questionId,
        @Schema(example = "5") int answeredCount,
        @Schema(example = "10") int eligibleCount
) {

    static AnswerProgressResponse from(AnswerProgressView view) {
        return new AnswerProgressResponse(
                view.sessionId(),
                view.questionId(),
                view.answeredCount(),
                view.eligibleCount());
    }
}
