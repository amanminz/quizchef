package io.quizchef.session.domain.exception;

import io.quizchef.common.exception.ConflictException;

/**
 * A question could not be pulled out of a session.
 *
 * <p>Distinct from {@link InvalidSessionTransitionException} because the
 * session is not in the wrong state — the removal is simply one the session
 * cannot survive, and the host needs to be told which. The only such case
 * today is the last remaining question: a session with nothing left to ask
 * would have to produce standings for a quiz nobody played, and refusing is
 * the safe outcome.
 */
public class QuestionRemovalNotAllowedException extends ConflictException {

    public QuestionRemovalNotAllowedException(String reason) {
        super("session.question.removal-not-allowed", reason);
    }
}
