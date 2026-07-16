package io.quizchef.session.domain.exception;

import io.quizchef.common.exception.ConflictException;
import io.quizchef.session.domain.ParticipantState;

/**
 * A participant was asked to move somewhere its current state does not
 * allow.
 */
public class InvalidParticipantTransitionException extends ConflictException {

    public InvalidParticipantTransitionException(ParticipantState from, String action) {
        super("participant.invalid-transition",
                "Cannot %s a participant in state %s".formatted(action, from));
    }
}
