package io.quizchef.common.exception;

/**
 * Base class for every QuizChef exception.
 *
 * <p>Carries a stable, machine-readable error code that becomes part of the API
 * contract. Subclasses express the error category; the API layer maps categories
 * to HTTP status codes so the domain never knows about HTTP.
 */
public abstract class QuizChefException extends RuntimeException {

    private final String errorCode;

    protected QuizChefException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
