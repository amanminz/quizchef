package io.quizchef.session.application;

import java.time.Instant;
import java.util.UUID;

/**
 * The code the host reads out, returned exactly once.
 *
 * <p>Carries the player's name so the host can check they are about to read
 * a code for the right person before they say it out loud.
 */
public record RecoveryCodeView(
        UUID participantId,
        String displayName,
        String code,
        Instant expiresAt
) {
}
