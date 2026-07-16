package io.quizchef.quiz.domain.exception;

import io.quizchef.common.exception.ConflictException;
import java.util.UUID;

/**
 * A quiz was required to be published but is not. A session can only run
 * content participants can rely on.
 */
public class QuizNotPublishedException extends ConflictException {

    public QuizNotPublishedException(UUID quizId) {
        super("quiz.not-published", "Quiz %s is not published".formatted(quizId));
    }
}
