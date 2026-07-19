package io.quizchef.quiz.domain.exception;

import io.quizchef.common.exception.ConflictException;

/**
 * A question cannot be restored in its current state. Restoring is the
 * inverse of archiving and only applies to ARCHIVED questions.
 */
public class QuestionNotRestorableException extends ConflictException {

    public QuestionNotRestorableException() {
        super("question.not-restorable", "Only archived questions can be restored");
    }
}
