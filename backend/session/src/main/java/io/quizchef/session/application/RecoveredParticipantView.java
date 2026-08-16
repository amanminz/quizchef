package io.quizchef.session.application;

/**
 * A recovered player: the fresh resume credential their new device stores,
 * and the state they are dropped back into.
 *
 * <p>The token is returned once, exactly like at join — and the old one it
 * replaces is dead from this moment.
 */
public record RecoveredParticipantView(
        String resumeToken,
        SessionSnapshotView session
) {
}
