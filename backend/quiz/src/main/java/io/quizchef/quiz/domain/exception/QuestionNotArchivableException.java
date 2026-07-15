package io.quizchef.quiz.domain.exception;

import io.quizchef.common.exception.ConflictException;

/**
 * A question cannot be archived in its current state. Archiving retires
 * published questions; drafts are simply edited or abandoned.
 */
public class QuestionNotArchivableException extends ConflictException {

    public QuestionNotArchivableException() {
        super("question.not-archivable", "Only published questions can be archived");
    }
}
