package io.quizchef.websocket.application;

import io.quizchef.session.domain.event.AnswerRevealedEvent;
import io.quizchef.session.domain.event.AnswerSubmittedEvent;
import io.quizchef.session.domain.event.LeaderboardUpdatedEvent;
import io.quizchef.session.domain.event.LobbyOpenedEvent;
import io.quizchef.session.domain.event.ParticipantDisconnectedEvent;
import io.quizchef.session.domain.event.ParticipantJoinedEvent;
import io.quizchef.session.domain.event.ParticipantReconnectedEvent;
import io.quizchef.session.domain.event.QuestionClosedEvent;
import io.quizchef.session.domain.event.QuestionStartedEvent;
import io.quizchef.session.domain.event.SessionFinishedEvent;
import io.quizchef.session.domain.event.SessionStartedEvent;
import io.quizchef.websocket.api.ProtocolMessage;
import io.quizchef.websocket.api.ProtocolMessageType;
import io.quizchef.websocket.api.event.AnswerRevealedPayload;
import io.quizchef.websocket.api.event.LeaderboardPayload;
import io.quizchef.websocket.api.event.ParticipantPayload;
import io.quizchef.websocket.api.event.QuestionPayload;
import io.quizchef.websocket.api.event.QuestionStartedPayload;
import java.util.List;

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

    static ProtocolMessage toMessage(QuestionStartedEvent event) {
        return ProtocolMessage.of(event.sessionId(), ProtocolMessageType.QUESTION_STARTED,
                event.occurredAt(),
                new QuestionStartedPayload(event.questionId(), event.endsAt(), event.durationSeconds()));
    }

    static ProtocolMessage toMessage(QuestionClosedEvent event) {
        return ProtocolMessage.of(event.sessionId(), ProtocolMessageType.QUESTION_CLOSED,
                event.occurredAt(), new QuestionPayload(event.questionId()));
    }

    static ProtocolMessage toMessage(AnswerRevealedEvent event) {
        return ProtocolMessage.of(event.sessionId(), ProtocolMessageType.ANSWER_REVEALED,
                event.occurredAt(),
                new AnswerRevealedPayload(event.questionId(), event.correctOptionIds()));
    }

    /**
     * A pure notification: the payload's rows are deliberately empty. The
     * session topic reaches every participant device, and the standings —
     * names, scores, ranks — are role-scoped reads since the live-event
     * privacy split (host: GET /results; participant: own result only).
     * Clients never treated the rows as a data source (events are
     * notifications; reads are authoritative), so nothing is lost.
     */
    static ProtocolMessage toMessage(LeaderboardUpdatedEvent event) {
        return ProtocolMessage.of(event.sessionId(), ProtocolMessageType.LEADERBOARD_UPDATED,
                event.occurredAt(), new LeaderboardPayload(List.of()));
    }

    static ProtocolMessage toMessage(AnswerSubmittedEvent event) {
        return ProtocolMessage.of(event.sessionId(), ProtocolMessageType.ANSWER_ACCEPTED,
                event.occurredAt(), new QuestionPayload(event.questionId()));
    }

    /**
     * The session-wide face of the same event: a pure "counts moved"
     * notification. Deliberately no participant id and no counts — the
     * authoritative numbers are the host-only answer-progress read, and
     * the broadcast must leak nothing about who answered.
     */
    static ProtocolMessage toProgressMessage(AnswerSubmittedEvent event) {
        return ProtocolMessage.of(event.sessionId(), ProtocolMessageType.ANSWER_PROGRESS,
                event.occurredAt(), new QuestionPayload(event.questionId()));
    }
}
