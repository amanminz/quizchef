package io.quizchef.session.domain.exception;

import io.quizchef.common.exception.ConflictException;

/**
 * The session's standings may not be read right now — the session has not
 * started, or a question is still being played (scores mid-question would
 * leak who has answered correctly before the reveal, ADR-006). A state,
 * not an error: clients simply wait for the reveal.
 */
public class ResultsNotAvailableException extends ConflictException {

    public ResultsNotAvailableException() {
        super("session.results.not-available",
                "Results are not available until the current question's answer is revealed");
    }
}
