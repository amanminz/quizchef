package io.quizchef.session.application;

import io.quizchef.session.domain.GuestParticipantToken;
import io.quizchef.session.domain.Participant;
import io.quizchef.session.domain.SessionState;
import java.util.UUID;

/**
 * What a player receives on joining: their participant id, their resume
 * token if they joined as a guest, and the session's current state.
 *
 * <p>This is the <strong>only</strong> place the resume token is ever
 * returned, and it is returned exactly once. The server keeps a digest, so
 * even it cannot produce the secret again — a player who loses it cannot be
 * handed it back, which is the point: a credential the server can re-issue
 * on request is a credential anyone can ask for.
 */
public record ParticipantSessionView(
        UUID participantId,
        UUID sessionId,
        String guestParticipantToken,
        SessionState sessionState
) {

    /**
     * The token is passed in rather than read off the participant, because
     * the participant does not have it — it holds only the digest. The
     * caller that generated the secret is the last thing that can see it.
     */
    static ParticipantSessionView of(Participant participant, GuestParticipantToken issuedToken,
                                     SessionState sessionState) {
        return new ParticipantSessionView(
                participant.getId(),
                participant.getSessionId(),
                issuedToken == null ? null : issuedToken.value(),
                sessionState);
    }
}
