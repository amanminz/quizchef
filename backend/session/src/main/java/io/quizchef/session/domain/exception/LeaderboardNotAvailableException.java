package io.quizchef.session.domain.exception;

import io.quizchef.common.exception.ConflictException;

/**
 * The interim leaderboard step does not exist for the question in play —
 * the quiz's last question goes straight from its reveal to the host's
 * winner ceremony, with no intermediate standings screen in between. A
 * state, not an error: the host's next step there is to finish the
 * session.
 */
public class LeaderboardNotAvailableException extends ConflictException {

    public LeaderboardNotAvailableException() {
        super("session.leaderboard.not-available",
                "The last question has no interim leaderboard — finish the session instead");
    }
}
