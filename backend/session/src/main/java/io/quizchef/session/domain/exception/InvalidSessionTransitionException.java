package io.quizchef.session.domain.exception;

import io.quizchef.common.exception.ConflictException;
import io.quizchef.session.domain.SessionState;

/**
 * A session was asked to move somewhere its current state does not allow.
 */
public class InvalidSessionTransitionException extends ConflictException {

    public InvalidSessionTransitionException(SessionState from, String action) {
        super("session.invalid-transition",
                "Cannot %s a session in state %s".formatted(action, from));
    }
}
