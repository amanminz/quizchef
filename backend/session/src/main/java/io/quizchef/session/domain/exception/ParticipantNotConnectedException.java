package io.quizchef.session.domain.exception;

import io.quizchef.common.exception.ConflictException;

/**
 * A participant tried to act while not connected. Answering requires an
 * active connection.
 */
public class ParticipantNotConnectedException extends ConflictException {

    public ParticipantNotConnectedException() {
        super("session.participant.not-connected", "The participant is not connected");
    }
}
