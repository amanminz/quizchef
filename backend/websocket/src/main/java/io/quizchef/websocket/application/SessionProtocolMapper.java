package io.quizchef.websocket.application;

import io.quizchef.session.domain.event.LobbyOpenedEvent;
import io.quizchef.session.domain.event.ParticipantDisconnectedEvent;
import io.quizchef.session.domain.event.ParticipantJoinedEvent;
import io.quizchef.session.domain.event.ParticipantReconnectedEvent;
import io.quizchef.session.domain.event.SessionFinishedEvent;
import io.quizchef.session.domain.event.SessionStartedEvent;
import io.quizchef.websocket.api.ProtocolMessage;
import io.quizchef.websocket.api.ProtocolMessageType;
import io.quizchef.websocket.api.event.ParticipantPayload;

/**
 * Projects session domain events onto the wire protocol.
 *
 * <p>This is the seam Aman's recommendation protects: a domain event
 * ({@code ParticipantReconnectedEvent}) becomes a protocol message typed
 * {@code participant.reconnected}, never the class name. Domain shapes can
 * change on one side of this mapper without the wire contract moving on the
 * other. It is pure projection — no business decisions, no state, no
 * side effects.
 */
final class SessionProtocolMapper {

    private SessionProtocolMapper() {
    }

    static ProtocolMessage toMessage(LobbyOpenedEvent event) {
        return ProtocolMessage.of(event.sessionId(), ProtocolMessageType.LOBBY_OPENED, event.occurredAt());
    }

    static ProtocolMessage toMessage(SessionStartedEvent event) {
        return ProtocolMessage.of(event.sessionId(), ProtocolMessageType.SESSION_STARTED, event.occurredAt());
    }

    static ProtocolMessage toMessage(SessionFinishedEvent event) {
        return ProtocolMessage.of(event.sessionId(), ProtocolMessageType.SESSION_FINISHED, event.occurredAt());
    }

    static ProtocolMessage toMessage(ParticipantJoinedEvent event) {
        return ProtocolMessage.of(event.sessionId(), ProtocolMessageType.PARTICIPANT_JOINED,
                event.occurredAt(), new ParticipantPayload(event.participantId()));
    }

    static ProtocolMessage toMessage(ParticipantDisconnectedEvent event) {
        return ProtocolMessage.of(event.sessionId(), ProtocolMessageType.PARTICIPANT_DISCONNECTED,
                event.occurredAt(), new ParticipantPayload(event.participantId()));
    }

    static ProtocolMessage toMessage(ParticipantReconnectedEvent event) {
        return ProtocolMessage.of(event.sessionId(), ProtocolMessageType.PARTICIPANT_RECONNECTED,
                event.occurredAt(), new ParticipantPayload(event.participantId()));
    }
}
