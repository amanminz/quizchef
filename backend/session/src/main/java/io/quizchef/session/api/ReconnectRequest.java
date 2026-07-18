package io.quizchef.session.api;

import io.quizchef.session.application.ReconnectSessionCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
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
        @Size(max = 128)
        String guestParticipantToken
) {

    /**
     * A structural check at the API boundary — Bean Validation recognizes
     * any {@code isXxx()} boolean method, record or not, as a constrained
     * property (Phase 3 PR #3 / RFC-011). The domain still owns the XOR
     * invariant for the actual reconnect; this only rejects the obviously
     * malformed request earlier.
     */
    @AssertTrue(message = "Exactly one of sessionId or guestParticipantToken must be present")
    public boolean isExactlyOneIdentifierPresent() {
        return (sessionId != null) ^ (guestParticipantToken != null);
    }

    ReconnectSessionCommand toCommand() {
        return new ReconnectSessionCommand(sessionId, guestParticipantToken);
    }
}
