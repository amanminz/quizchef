package io.quizchef.websocket.api.command;

/**
 * Marker for inbound protocol commands — what a client asks the server to
 * do.
 *
 * <p><strong>Definitions only in this PR.</strong> No handlers, no
 * {@code @MessageMapping}: per ADR-005 a command will be validated at the
 * transport edge and delegated to an application service (never handled in
 * the websocket module), and those services arrive with Session APIs. These
 * types settle the inbound vocabulary now.
 */
public sealed interface ProtocolCommand permits HostCommand, ParticipantCommand {
}
