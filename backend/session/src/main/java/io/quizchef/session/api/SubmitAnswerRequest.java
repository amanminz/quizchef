package io.quizchef.session.api;

import io.quizchef.session.application.SubmitAnswerCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;

/**
 * A participant's answer to the open question. The participant is named by
 * the id they received on join; the server stamps the time and scores it.
 */
public record SubmitAnswerRequest(
        @Schema(description = "The participant id returned when joining") @NotNull UUID participantId,
        @NotNull UUID questionId,
        @Schema(description = "Chosen option ids; must be options of this question")
        @NotEmpty @Size(max = 20) Set<UUID> selectedOptionIds
) {

    SubmitAnswerCommand toCommand() {
        return new SubmitAnswerCommand(participantId, questionId, selectedOptionIds);
    }
}
