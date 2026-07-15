package io.quizchef.quiz.domain.exception;

import io.quizchef.common.exception.ResourceNotFoundException;
import java.util.UUID;

/**
 * No question with the given id is visible to the caller. Also raised for
 * questions of other owners — their existence is not disclosed.
 */
public class QuestionNotFoundException extends ResourceNotFoundException {

    public QuestionNotFoundException(UUID questionId) {
        super("question.not-found", "Question %s does not exist".formatted(questionId));
    }
}
