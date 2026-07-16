package io.quizchef.websocket.api.event;

import io.quizchef.websocket.api.ProtocolPayload;
import java.util.Objects;
import java.util.UUID;

/**
 * The body of the roster messages ({@code participant.joined},
 * {@code participant.disconnected}, {@code participant.reconnected}): which
 * participant the message is about. The session is already in the envelope.
 *
 * <p>Only the participant id crosses the wire here — display name and other
 * details are delivered through their own messages or the reconnection
 * snapshot, keeping roster churn cheap.
 */
public record ParticipantPayload(UUID participantId) implements ProtocolPayload {

    public ParticipantPayload {
        Objects.requireNonNull(participantId, "participantId must not be null");
    }
}
