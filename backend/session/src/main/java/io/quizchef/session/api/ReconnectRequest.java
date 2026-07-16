package io.quizchef.session.api;

import io.quizchef.session.application.ReconnectSessionCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * Reconnects to an existing participant. A guest sends the token they were
 * given on join; a registered player sends the session id and reconnects
 * through their bearer token.
 */
public record ReconnectRequest(
        @Schema(description = "Required for a registered reconnect; omit for a guest")
        UUID sessionId,
        @Schema(description = "The guest's reconnection token; omit for a registered reconnect")
        String guestParticipantToken
) {

    ReconnectSessionCommand toCommand() {
        return new ReconnectSessionCommand(sessionId, guestParticipantToken);
    }
}
