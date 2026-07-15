package io.quizchef.quiz.domain.exception;

import io.quizchef.common.exception.ConflictException;

/**
 * The client's version of the question is stale: someone else saved after
 * the client last read it.
 */
public class QuestionModifiedConcurrentlyException extends ConflictException {

    public QuestionModifiedConcurrentlyException() {
        super("question.concurrent-modification",
                "This question has been modified by someone else. Refresh before saving.");
    }
}
