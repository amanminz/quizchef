package io.quizchef.quiz.domain.exception;

import io.quizchef.common.exception.ResourceNotFoundException;
import java.util.UUID;

/**
 * No quiz with the given id is visible to the caller. Also raised for
 * private quizzes of other owners — their existence is not disclosed.
 */
public class QuizNotFoundException extends ResourceNotFoundException {

    public QuizNotFoundException(UUID quizId) {
        super("quiz.not-found", "Quiz %s does not exist".formatted(quizId));
    }
}
