package io.quizchef.common.exception;

/**
 * A requested resource does not exist. Maps to HTTP 404.
 */
public class ResourceNotFoundException extends QuizChefException {

    public ResourceNotFoundException(String errorCode, String message) {
        super(errorCode, message);
    }
}
