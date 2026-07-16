package io.quizchef.session.domain.exception;

import io.quizchef.common.exception.ResourceNotFoundException;

/**
 * No session matches the given PIN or id (among active sessions).
 */
public class SessionNotFoundException extends ResourceNotFoundException {

    public SessionNotFoundException(String message) {
        super("session.not-found", message);
    }
}
