package io.quizchef.session.domain.exception;

import io.quizchef.common.exception.ConflictException;

/**
 * Someone in this session is already playing under that name.
 *
 * <p>The rule exists for the room, not the database: a leaderboard with two
 * "Aman" rows cannot be read out loud, and a host cannot tell which one to
 * congratulate. It is also what makes losing a resume token legible — the
 * player is told the name is taken instead of silently becoming a second
 * participant with nobody's score.
 *
 * <p>It is deliberately not an identity check. Holding the resume token for
 * the existing "Aman" resumes them and never reaches this rule; not holding
 * it means the server has no way to know whether this is the same person,
 * and guessing from a name is exactly the mistake the token exists to
 * prevent.
 */
public class DisplayNameAlreadyTakenException extends ConflictException {

    public DisplayNameAlreadyTakenException(String displayName) {
        super("participant.name-already-taken",
                "\"%s\" is already part of this quiz".formatted(displayName));
    }
}
