package io.quizchef.quiz.domain.exception;

import io.quizchef.common.exception.ConflictException;

/**
 * The client's version of the quiz is stale: someone else saved after the
 * client last read it. Without this check the second save would silently
 * overwrite the first author's work.
 */
public class QuizModifiedConcurrentlyException extends ConflictException {

    public QuizModifiedConcurrentlyException() {
        super("quiz.concurrent-modification",
                "This quiz has been modified by someone else. Refresh before saving.");
    }
}
