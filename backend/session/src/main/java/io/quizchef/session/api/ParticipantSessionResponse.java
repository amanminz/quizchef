package io.quizchef.session.api;

import io.quizchef.session.application.ParticipantSessionView;
import io.quizchef.session.domain.SessionState;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * The join result. A guest receives their {@code guestParticipantToken} here
 * and only here — it is the secret they store to reconnect. A registered
 * player's token is null (they reconnect through their identity).
 */
public record ParticipantSessionResponse(
        UUID participantId,
        UUID sessionId,
        @Schema(description = "The guest's reconnection token; null for a registered participant")
        String guestParticipantToken,
        SessionState sessionState
) {

    static ParticipantSessionResponse from(ParticipantSessionView view) {
        return new ParticipantSessionResponse(
                view.participantId(),
                view.sessionId(),
                view.guestParticipantToken(),
                view.sessionState());
    }
}
