package io.quizchef.session.domain.exception;

import io.quizchef.common.exception.ConflictException;

/**
 * An answer was rejected by the server — the question is not open, it was
 * already answered, or it targets a different question than the one in play.
 * Answer acceptance is server-authoritative (ADR-006).
 */
public class AnswerNotAcceptedException extends ConflictException {

    public AnswerNotAcceptedException(String message) {
        super("session.answer.not-accepted", message);
    }
}
