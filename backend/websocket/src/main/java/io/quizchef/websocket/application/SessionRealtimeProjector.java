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
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Subscribes to session domain events and projects each onto the realtime
 * protocol, broadcasting to the session's audience through the {@link
 * RealtimePublisher} port.
 *
 * <p>This is the whole of the outbound realtime path, and it is strictly
 * one-directional (ADR-004/005): domain events come in, protocol messages go
 * out. It never calls into a domain module, never mutates state, never
 * decides anything — it translates and delivers. Inbound commands are the
 * mirror image (application services, a later PR), never handled here.
 *
 * <p>{@code SessionCreatedEvent} is deliberately not projected: it happens
 * before anyone has connected, so there is no audience for it.
 */
@Component
public class SessionRealtimeProjector {

    private final RealtimePublisher realtimePublisher;

    public SessionRealtimeProjector(RealtimePublisher realtimePublisher) {
        this.realtimePublisher = realtimePublisher;
    }

    @EventListener
    void on(LobbyOpenedEvent event) {
        realtimePublisher.publish(SessionProtocolMapper.toMessage(event));
    }

    @EventListener
    void on(SessionStartedEvent event) {
        realtimePublisher.publish(SessionProtocolMapper.toMessage(event));
    }

    @EventListener
    void on(SessionFinishedEvent event) {
        realtimePublisher.publish(SessionProtocolMapper.toMessage(event));
    }

    @EventListener
    void on(ParticipantJoinedEvent event) {
        realtimePublisher.publish(SessionProtocolMapper.toMessage(event));
    }

    @EventListener
    void on(ParticipantDisconnectedEvent event) {
        realtimePublisher.publish(SessionProtocolMapper.toMessage(event));
    }

    @EventListener
    void on(ParticipantReconnectedEvent event) {
        realtimePublisher.publish(SessionProtocolMapper.toMessage(event));
    }

    @EventListener
    void on(QuestionStartedEvent event) {
        realtimePublisher.publish(SessionProtocolMapper.toMessage(event));
    }

    @EventListener
    void on(QuestionClosedEvent event) {
        realtimePublisher.publish(SessionProtocolMapper.toMessage(event));
    }

    @EventListener
    void on(AnswerRevealedEvent event) {
        realtimePublisher.publish(SessionProtocolMapper.toMessage(event));
    }

    @EventListener
    void on(LeaderboardUpdatedEvent event) {
        realtimePublisher.publish(SessionProtocolMapper.toMessage(event));
    }

    /**
     * The answer acknowledgement is private — it goes to the submitting
     * participant only, never the whole session (ADR-006: no score leaks, and
     * an opponent learns nothing from your submission).
     */
    @EventListener
    void on(AnswerSubmittedEvent event) {
        realtimePublisher.publishToParticipant(
                event.participantId(), SessionProtocolMapper.toMessage(event));
    }
}
