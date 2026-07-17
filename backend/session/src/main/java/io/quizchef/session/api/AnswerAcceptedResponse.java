package io.quizchef.session.api;

import io.quizchef.session.application.AnswerAcceptedView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * Acknowledges a submitted answer — receipt only. The score is deliberately
 * absent; points are revealed by the server, never returned here (ADR-006).
 */
public record AnswerAcceptedResponse(
        UUID participantId,
        UUID questionId,
        @Schema(example = "true") boolean accepted
) {

    static AnswerAcceptedResponse from(AnswerAcceptedView view) {
        return new AnswerAcceptedResponse(view.participantId(), view.questionId(), true);
    }
}
