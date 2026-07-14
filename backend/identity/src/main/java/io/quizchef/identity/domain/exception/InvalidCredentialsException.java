package io.quizchef.identity.domain.exception;

import io.quizchef.common.exception.UnauthorizedException;

/**
 * The presented credentials do not match.
 *
 * <p>The message never reveals whether the email or the password was wrong.
 */
public class InvalidCredentialsException extends UnauthorizedException {

    public InvalidCredentialsException() {
        super("identity.credentials.invalid", "Invalid email or password");
    }
}
