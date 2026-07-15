package io.quizchef.quiz.domain.exception;

import io.quizchef.common.exception.ConflictException;
import java.util.UUID;

/**
 * A question was added to a quiz that already contains it.
 */
public class DuplicateQuizQuestionException extends ConflictException {

    public DuplicateQuizQuestionException(UUID questionId) {
        super("quiz.question.duplicate", "Question %s is already part of this quiz".formatted(questionId));
    }
}
