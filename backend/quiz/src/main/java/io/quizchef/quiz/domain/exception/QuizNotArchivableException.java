package io.quizchef.quiz.domain.exception;

import io.quizchef.common.exception.ConflictException;

/**
 * A quiz cannot be archived in its current state. Archiving retires
 * published quizzes; drafts are simply edited or abandoned.
 */
public class QuizNotArchivableException extends ConflictException {

    public QuizNotArchivableException() {
        super("quiz.not-archivable", "Only published quizzes can be archived");
    }
}
