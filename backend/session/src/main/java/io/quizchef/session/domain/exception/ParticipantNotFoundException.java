package io.quizchef.session.domain.exception;

import io.quizchef.common.exception.ResourceNotFoundException;

/**
 * No participant matches the reconnection details — an unknown guest token,
 * or no participant for the caller's identity in the given session.
 */
public class ParticipantNotFoundException extends ResourceNotFoundException {

    public ParticipantNotFoundException() {
        super("session.participant.not-found", "No participant matches these reconnection details");
    }
}
