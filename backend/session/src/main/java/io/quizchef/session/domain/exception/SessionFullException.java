package io.quizchef.session.domain.exception;

import io.quizchef.common.exception.ConflictException;

/**
 * A participant tried to join a session that has reached its participant cap.
 */
public class SessionFullException extends ConflictException {

    public SessionFullException(int maxParticipants) {
        super("session.full",
                "This session is full (%d participants)".formatted(maxParticipants));
    }
}
