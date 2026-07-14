package io.quizchef.common.exception;

/**
 * The caller is not authenticated or presented invalid proof of identity.
 * Maps to HTTP 401.
 */
public class UnauthorizedException extends QuizChefException {

    public UnauthorizedException(String errorCode, String message) {
        super(errorCode, message);
    }

    public UnauthorizedException() {
        this("auth.unauthorized", "Authentication is required");
    }
}
