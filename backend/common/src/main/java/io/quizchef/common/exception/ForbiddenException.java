package io.quizchef.common.exception;

/**
 * The caller is authenticated but lacks permission. Maps to HTTP 403.
 */
public class ForbiddenException extends QuizChefException {

    public ForbiddenException(String errorCode, String message) {
        super(errorCode, message);
    }

    public ForbiddenException() {
        this("auth.forbidden", "Access is denied");
    }
}
