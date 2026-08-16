package io.quizchef.session.domain.exception;

import io.quizchef.common.exception.ConflictException;

/**
 * Host-assisted recovery does not apply here — a signed-in player, or a
 * quiz that has already finished.
 */
public class RecoveryNotAvailableException extends ConflictException {

    public RecoveryNotAvailableException(String reason) {
        super("participant.recovery.not-available", reason);
    }
}
