package io.quizchef.identity.domain.exception;

import io.quizchef.common.exception.ConflictException;

/**
 * The email address is already registered.
 *
 * <p>The message deliberately omits the address itself so it never reaches
 * logs or API responses as PII.
 */
public class DuplicateEmailException extends ConflictException {

    public DuplicateEmailException() {
        super("identity.email.duplicate", "Email address is already registered");
    }
}
