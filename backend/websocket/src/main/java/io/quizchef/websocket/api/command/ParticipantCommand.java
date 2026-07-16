package io.quizchef.websocket.api.command;

import java.util.Set;
import java.util.UUID;

/**
 * Commands a participant may issue. Definitions only (see {@link
 * ProtocolCommand}); each will be delegated to an application service that
 * owns the rules — joining, answering, reconnecting — none of which live in
 * the websocket module.
 */
public sealed interface ParticipantCommand extends ProtocolCommand {

    /**
     * Join a session by PIN. A guest supplies no token here and receives one
     * on join; a returning guest reconnects via {@link Reconnect}.
     *
     * @param preferredLanguage BCP-47 tag the participant wants to play in
     */
    record JoinSession(String sessionPin, String displayName, String preferredLanguage)
            implements ParticipantCommand {
    }

    /** Submit an answer to the current question. */
    record SubmitAnswer(UUID sessionId, UUID questionId, Set<UUID> selectedOptionIds)
            implements ParticipantCommand {
    }

    /**
     * Rebind to an existing participant after a disconnect. A registered user
     * reconnects through their identity (established at the transport layer);
     * a guest presents the token they were given on join.
     */
    record Reconnect(UUID sessionId, String guestParticipantToken) implements ParticipantCommand {
    }
}
