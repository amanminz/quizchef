package io.quizchef.quiz.domain.exception;

import io.quizchef.common.exception.ConflictException;

/**
 * A published quiz was asked to drop a question. Published quizzes may
 * gain questions but never lose them — participants may already rely on
 * the composition.
 */
public class QuizQuestionsLockedException extends ConflictException {

    public QuizQuestionsLockedException() {
        super("quiz.questions.locked", "Published quizzes cannot lose questions");
    }
}
