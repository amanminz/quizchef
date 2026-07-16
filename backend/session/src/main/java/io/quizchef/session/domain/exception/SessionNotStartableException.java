package io.quizchef.session.domain.exception;

import io.quizchef.common.exception.ConflictException;

/**
 * A session cannot start in its current shape — for example, with no
 * participants in the lobby.
 */
public class SessionNotStartableException extends ConflictException {

    public SessionNotStartableException(String message) {
        super("session.not-startable", message);
    }
}
