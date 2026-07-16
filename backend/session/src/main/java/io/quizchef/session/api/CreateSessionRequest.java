package io.quizchef.session.api;

import io.quizchef.session.application.CreateSessionCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Creates a session for a published quiz version. The host is the caller —
 * never in the request.
 */
public record CreateSessionRequest(
        @Schema(description = "The published quiz version to run")
        @NotNull UUID publishedQuizVersionId
) {

    CreateSessionCommand toCommand() {
        return new CreateSessionCommand(publishedQuizVersionId);
    }
}
