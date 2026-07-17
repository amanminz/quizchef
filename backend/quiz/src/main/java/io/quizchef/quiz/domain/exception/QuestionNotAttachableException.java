package io.quizchef.quiz.domain.exception;

import io.quizchef.common.exception.ConflictException;

/**
 * A question cannot be attached to a quiz in its current state — an
 * archived question is retired from new use. Draft and published questions
 * are both attachable.
 */
public class QuestionNotAttachableException extends ConflictException {

    public QuestionNotAttachableException(String message) {
        super("quiz.question.not-attachable", message);
    }
}
