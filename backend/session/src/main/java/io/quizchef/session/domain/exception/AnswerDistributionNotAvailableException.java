package io.quizchef.session.domain.exception;

import io.quizchef.common.exception.ConflictException;

/**
 * The current question's answer distribution may not be read yet — the
 * question has not been revealed. A state, not an error: the host simply
 * waits for the reveal, exactly like the full results read.
 */
public class AnswerDistributionNotAvailableException extends ConflictException {

    public AnswerDistributionNotAvailableException() {
        super("session.distribution.not-available",
                "Answer distribution is not available until the current question's answer is revealed");
    }
}
