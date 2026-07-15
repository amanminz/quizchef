package io.quizchef.quiz.domain.exception;

import io.quizchef.common.exception.ConflictException;

/**
 * An archived question was asked to change. Archived questions are
 * read-only and archiving is terminal.
 */
public class QuestionArchivedException extends ConflictException {

    public QuestionArchivedException() {
        super("question.archived", "Archived questions cannot be modified");
    }
}
