package io.quizchef.session.domain.exception;

import io.quizchef.common.exception.ConflictException;

/**
 * A participant's ranking-neighbour context may not be read right now —
 * either the current question's answer has not been revealed yet, or this
 * is the quiz's final question, whose neighbours are never disclosed
 * (final standings are held for the host's winner ceremony instead). A
 * state, not an error.
 */
public class RankContextNotAvailableException extends ConflictException {

    public RankContextNotAvailableException() {
        super("session.rank-context.not-available",
                "Ranking neighbours are not available for this question right now");
    }
}
