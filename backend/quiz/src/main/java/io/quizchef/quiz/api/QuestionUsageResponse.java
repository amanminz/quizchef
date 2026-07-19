package io.quizchef.quiz.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * How many quizzes compose a question — the fact the delete affordance is
 * framed with: zero means deletable, more means the quiz references must
 * be removed first.
 */
public record QuestionUsageResponse(
        UUID questionId,
        @Schema(example = "2") long quizCount
) {
}
