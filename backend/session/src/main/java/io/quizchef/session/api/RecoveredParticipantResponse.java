package io.quizchef.session.api;

import io.quizchef.session.application.RecoveredParticipantView;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A recovered player: the fresh resume credential their new device stores,
 * and the state they are dropped back into.
 *
 * <p>Shaped exactly like a join followed by a resume, because that is what
 * it replaces — the token is returned once, and the one it supersedes is
 * dead from this moment.
 */
public record RecoveredParticipantResponse(
        @Schema(description = "Store this; the previous credential no longer works")
        String resumeToken,
        SessionSnapshotResponse session
) {

    static RecoveredParticipantResponse from(RecoveredParticipantView view) {
        return new RecoveredParticipantResponse(
                view.resumeToken(), SessionSnapshotResponse.from(view.session()));
    }
}
