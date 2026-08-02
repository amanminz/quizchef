package io.quizchef.session.domain.exception;

import io.quizchef.common.exception.ConflictException;

/**
 * The animated Top 5 transition may not be read right now — no answer has
 * been revealed yet, or the question in play is the quiz's last one, whose
 * standings are held for the host's winner ceremony instead of ever being
 * projected as an interim leaderboard. A state, not an error: the host
 * client simply moves to the final-results flow.
 */
public class TopFiveLeaderboardNotAvailableException extends ConflictException {

    public TopFiveLeaderboardNotAvailableException() {
        super("session.top-five.not-available",
                "The Top 5 leaderboard is not available for this question");
    }
}
