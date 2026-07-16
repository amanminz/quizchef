package io.quizchef.websocket.api.command;

import java.util.UUID;

/**
 * Commands only the host may issue — the controls that drive a session
 * forward. Definitions only (see {@link ProtocolCommand}); the host's
 * authority will be checked by the application service that handles each,
 * never here.
 */
public sealed interface HostCommand extends ProtocolCommand {

    /** Move the session from CREATED to LOBBY. */
    record OpenLobby(UUID sessionId) implements HostCommand {
    }

    /** Begin play. */
    record StartSession(UUID sessionId) implements HostCommand {
    }

    /** Open a specific question for answering. */
    record StartQuestion(UUID sessionId, UUID questionId) implements HostCommand {
    }

    /** Close answering and reveal the correct answer for the current question. */
    record RevealAnswer(UUID sessionId, UUID questionId) implements HostCommand {
    }

    /** Show the standings between questions. */
    record ShowLeaderboard(UUID sessionId) implements HostCommand {
    }

    /** End the session. */
    record FinishSession(UUID sessionId) implements HostCommand {
    }
}
