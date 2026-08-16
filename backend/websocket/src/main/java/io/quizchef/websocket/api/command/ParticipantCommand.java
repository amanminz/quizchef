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
     * on join; a returning guest comes back via {@link Resume}.
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
     * Return to an existing participant. A registered user resumes through
     * their identity (established at the transport layer); a guest presents
     * the resume token they were given on join.
     *
     * <p>Addressed by PIN, matching the REST endpoint: the PIN is the only
     * handle the player actually has, and resolving it means a credential
     * left over from an earlier session that reused the same code cannot
     * quietly restore them into the wrong quiz.
     */
    record Resume(String sessionPin, String resumeToken) implements ParticipantCommand {
    }
}
