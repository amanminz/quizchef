package io.quizchef.session.api;

import io.quizchef.session.application.ResumeParticipantCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * A player's claim to be someone already in this session.
 *
 * <p>A guest sends the resume token issued at join. A registered player
 * sends nothing at all and is resolved from their bearer identity — the
 * body is then empty, which is why no field is required.
 *
 * <p>There is deliberately no participant id here. It travels in URLs and
 * in this session's own responses, so accepting it as proof would let
 * anyone who saw one inherit that player's score.
 */
public record ResumeParticipantRequest(
        @Schema(description = "The guest's resume token, issued once at join; omit when signed in")
        @Size(max = 128)
        String resumeToken
) {

    ResumeParticipantCommand toCommand(String sessionPin) {
        return new ResumeParticipantCommand(sessionPin, resumeToken);
    }
}
