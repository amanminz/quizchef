package io.quizchef.quiz.domain.exception;

import io.quizchef.common.exception.ConflictException;

/**
 * A published quiz was asked to change content or settings. After
 * publication only visibility may change — participants may already rely
 * on everything else.
 */
public class QuizContentLockedException extends ConflictException {

    public QuizContentLockedException() {
        super("quiz.content.locked", "Published quizzes may only change visibility");
    }
}
