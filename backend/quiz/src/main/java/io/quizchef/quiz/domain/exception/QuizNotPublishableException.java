package io.quizchef.quiz.domain.exception;

import io.quizchef.common.exception.ConflictException;

/**
 * A quiz cannot be published in its current shape or state.
 */
public class QuizNotPublishableException extends ConflictException {

    public QuizNotPublishableException(String message) {
        super("quiz.not-publishable", message);
    }
}
