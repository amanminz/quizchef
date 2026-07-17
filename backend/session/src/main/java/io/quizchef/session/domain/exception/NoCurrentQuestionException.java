package io.quizchef.session.domain.exception;

import io.quizchef.common.exception.ConflictException;
import io.quizchef.session.domain.SessionState;

/**
 * The session has no question in play right now — it is not IN_PROGRESS,
 * or gameplay has not opened its first question yet. A state, not an
 * error: clients between questions simply wait for the next
 * question.started event.
 */
public class NoCurrentQuestionException extends ConflictException {

    public NoCurrentQuestionException(SessionState state) {
        super("session.no-current-question",
                "No question is in play (session is %s)".formatted(state));
    }
}
