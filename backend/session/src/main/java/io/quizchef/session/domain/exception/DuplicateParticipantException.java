package io.quizchef.session.domain.exception;

import io.quizchef.common.exception.ConflictException;
import java.util.UUID;

/**
 * A participant already on the roster was registered again.
 */
public class DuplicateParticipantException extends ConflictException {

    public DuplicateParticipantException(UUID participantId) {
        super("session.participant.duplicate",
                "Participant %s is already in this session".formatted(participantId));
    }
}
