package io.quizchef.session.domain.exception;

import io.quizchef.common.exception.ConflictException;

/**
 * The recovery code was not accepted.
 *
 * <p>One message for every reason — unknown digits, expired, already used,
 * or issued for a different session. A player who mistypes and a stranger
 * guessing get the same answer, so the response cannot be used to narrow
 * down which codes exist.
 */
public class RecoveryCodeNotAcceptedException extends ConflictException {

    public RecoveryCodeNotAcceptedException() {
        super("participant.recovery.code-not-accepted",
                "That recovery code is not valid. Ask the Quiz Master for a new one.");
    }
}
