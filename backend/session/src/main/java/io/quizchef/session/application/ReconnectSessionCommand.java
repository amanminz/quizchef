package io.quizchef.session.application;

import java.util.UUID;

/**
 * Reconnects to an existing participant. A guest presents the token they
 * were given on join (globally unique, so no session id is needed); a
 * registered player is found by their authenticated identity within the
 * given session.
 *
 * @param sessionId              required for a registered reconnect; ignored for a guest
 * @param guestParticipantToken  present for a guest reconnect; null for a registered one
 */
public record ReconnectSessionCommand(
        UUID sessionId,
        String guestParticipantToken
) {
}
