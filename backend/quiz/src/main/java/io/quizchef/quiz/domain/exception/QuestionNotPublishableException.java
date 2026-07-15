package io.quizchef.quiz.domain.exception;

import io.quizchef.common.exception.ConflictException;

/**
 * A question cannot be published in its current state.
 */
public class QuestionNotPublishableException extends ConflictException {

    public QuestionNotPublishableException(String message) {
        super("question.not-publishable", message);
    }
}
