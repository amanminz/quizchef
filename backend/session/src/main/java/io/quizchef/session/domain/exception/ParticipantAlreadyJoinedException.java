package io.quizchef.session.domain.exception;

import io.quizchef.common.exception.ConflictException;

/**
 * The same identity or guest token is already on the session's roster. A
 * person joins a session once; a second join reconnects the existing
 * participant rather than creating another.
 */
public class ParticipantAlreadyJoinedException extends ConflictException {

    public ParticipantAlreadyJoinedException() {
        super("session.participant.already-joined",
                "This identity or guest token has already joined the session");
    }
}
