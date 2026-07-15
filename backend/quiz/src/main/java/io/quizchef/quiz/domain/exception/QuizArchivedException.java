package io.quizchef.quiz.domain.exception;

import io.quizchef.common.exception.ConflictException;

/**
 * An archived quiz was asked to change. Archived quizzes are read-only.
 */
public class QuizArchivedException extends ConflictException {

    public QuizArchivedException() {
        super("quiz.archived", "Archived quizzes cannot be modified");
    }
}
