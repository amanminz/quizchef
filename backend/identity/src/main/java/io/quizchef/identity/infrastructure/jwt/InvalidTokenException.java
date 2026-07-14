package io.quizchef.identity.infrastructure.jwt;

import io.quizchef.common.exception.UnauthorizedException;

/**
 * The presented token could not be verified.
 *
 * <p>Expiry gets its own error code so clients can distinguish "refresh your
 * token" from "this token was never valid".
 */
public class InvalidTokenException extends UnauthorizedException {

    private InvalidTokenException(String errorCode, String message) {
        super(errorCode, message);
    }

    public static InvalidTokenException malformed() {
        return new InvalidTokenException("identity.token.invalid", "Token is invalid");
    }

    public static InvalidTokenException expired() {
        return new InvalidTokenException("identity.token.expired", "Token has expired");
    }
}
