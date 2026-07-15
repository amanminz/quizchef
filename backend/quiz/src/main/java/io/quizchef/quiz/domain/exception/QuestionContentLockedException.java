package io.quizchef.quiz.domain.exception;

import io.quizchef.common.exception.ConflictException;

/**
 * A published question was asked to change. Published questions are
 * immutable — quizzes may already rely on them.
 */
public class QuestionContentLockedException extends ConflictException {

    public QuestionContentLockedException() {
        super("question.content.locked", "Published questions are immutable");
    }
}
