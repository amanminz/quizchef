package io.quizchef.session.api;

import io.quizchef.session.application.RecoveryCodeView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * The code the host reads out, returned exactly once.
 *
 * <p>Carries the player's name so the host can check they are about to read
 * a code for the right person before saying it out loud.
 */
public record RecoveryCodeResponse(
        UUID participantId,
        @Schema(example = "Aman") String displayName,
        @Schema(description = "Six digits, valid for a few minutes and usable once",
                example = "482731")
        String code,
        Instant expiresAt
) {

    static RecoveryCodeResponse from(RecoveryCodeView view) {
        return new RecoveryCodeResponse(view.participantId(), view.displayName(),
                view.code(), view.expiresAt());
    }
}
