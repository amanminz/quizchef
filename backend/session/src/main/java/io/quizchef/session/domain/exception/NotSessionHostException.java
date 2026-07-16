package io.quizchef.session.domain.exception;

import io.quizchef.common.exception.ForbiddenException;

/**
 * A caller who is not the session's host tried to control it. Hosting a
 * session is exclusive to the identity that created it.
 */
public class NotSessionHostException extends ForbiddenException {

    public NotSessionHostException() {
        super("session.host.required", "Only the session host can perform this action");
    }
}
