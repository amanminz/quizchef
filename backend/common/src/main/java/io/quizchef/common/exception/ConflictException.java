package io.quizchef.common.exception;

/**
 * The request conflicts with existing state. Maps to HTTP 409.
 */
public class ConflictException extends QuizChefException {

    public ConflictException(String errorCode, String message) {
        super(errorCode, message);
    }
}
