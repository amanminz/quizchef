package io.quizchef.session.application;

import java.util.UUID;

/**
 * One roster row as the host's lobby wall shows it: who, by name, and
 * whether they are currently connected.
 */
public record SessionRosterMemberView(
        UUID participantId,
        String displayName,
        boolean connected
) {
}
