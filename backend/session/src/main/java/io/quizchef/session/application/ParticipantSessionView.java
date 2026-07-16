package io.quizchef.session.application;

import io.quizchef.session.domain.Participant;
import io.quizchef.session.domain.SessionState;
import java.util.UUID;

/**
 * What a player receives on joining: their participant id, their guest token
 * if they joined as a guest (the secret they store to reconnect — returned
 * exactly once, here), and the session's current state.
 */
public record ParticipantSessionView(
        UUID participantId,
        UUID sessionId,
        String guestParticipantToken,
        SessionState sessionState
) {

    public static ParticipantSessionView of(Participant participant, SessionState sessionState) {
        return new ParticipantSessionView(
                participant.getId(),
                participant.getSessionId(),
                participant.isGuest() ? participant.getGuestParticipantToken().value() : null,
                sessionState);
    }
}
