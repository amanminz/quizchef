package io.quizchef.websocket.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.quizchef.session.domain.event.LobbyOpenedEvent;
import io.quizchef.session.domain.event.ParticipantDisconnectedEvent;
import io.quizchef.session.domain.event.ParticipantJoinedEvent;
import io.quizchef.session.domain.event.ParticipantReconnectedEvent;
import io.quizchef.session.domain.event.SessionFinishedEvent;
import io.quizchef.session.domain.event.SessionStartedEvent;
import io.quizchef.websocket.api.ProtocolMessage;
import io.quizchef.websocket.api.ProtocolMessageType;
import io.quizchef.websocket.api.ProtocolVersion;
import io.quizchef.websocket.api.event.ParticipantPayload;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Verifies the outbound projection: each session domain event becomes the
 * right protocol message, broadcast through the port — with the stable wire
 * type, the session id, the event's own timestamp, and the current protocol
 * version.
 */
class SessionRealtimeProjectorTest {

    private static final Instant NOW = Instant.parse("2026-07-16T10:00:00Z");

    private final CapturingPublisher publisher = new CapturingPublisher();
    private final SessionRealtimeProjector projector = new SessionRealtimeProjector(publisher);

    private final UUID sessionId = UUID.randomUUID();
    private final UUID participantId = UUID.randomUUID();

    @Test
    void projectsSessionLifecycleEventsAsEnvelopeOnlyMessages() {
        projector.on(new LobbyOpenedEvent(sessionId, NOW));
        projector.on(new SessionStartedEvent(sessionId, NOW));
        projector.on(new SessionFinishedEvent(sessionId, NOW));

        assertThat(publisher.broadcasts)
                .extracting(ProtocolMessage::type)
                .containsExactly(ProtocolMessageType.LOBBY_OPENED,
                        ProtocolMessageType.SESSION_STARTED,
                        ProtocolMessageType.SESSION_FINISHED);
        assertThat(publisher.broadcasts).allSatisfy(message -> {
            assertThat(message.sessionId()).isEqualTo(sessionId);
            assertThat(message.occurredAt()).isEqualTo(NOW);
            assertThat(message.protocolVersion()).isEqualTo(ProtocolVersion.CURRENT);
            assertThat(message.messageId()).isNotNull();
            assertThat(message.payload()).isNull();
        });
    }

    @Test
    void projectsParticipantEventsWithTheParticipantPayload() {
        projector.on(new ParticipantJoinedEvent(sessionId, participantId, NOW));
        projector.on(new ParticipantDisconnectedEvent(sessionId, participantId, NOW));
        projector.on(new ParticipantReconnectedEvent(sessionId, participantId, NOW));

        assertThat(publisher.broadcasts)
                .extracting(ProtocolMessage::type)
                .containsExactly(ProtocolMessageType.PARTICIPANT_JOINED,
                        ProtocolMessageType.PARTICIPANT_DISCONNECTED,
                        ProtocolMessageType.PARTICIPANT_RECONNECTED);
        assertThat(publisher.broadcasts).allSatisfy(message ->
                assertThat(((ParticipantPayload) message.payload()).participantId())
                        .isEqualTo(participantId));
    }

    @Test
    void everyMessageIdIsUnique() {
        projector.on(new LobbyOpenedEvent(sessionId, NOW));
        projector.on(new LobbyOpenedEvent(sessionId, NOW));

        assertThat(publisher.broadcasts).extracting(ProtocolMessage::messageId).doesNotHaveDuplicates();
    }

    @Test
    void projectsGameplayEventsWithTheirPayloads() {
        UUID questionId = UUID.randomUUID();
        projector.on(new io.quizchef.session.domain.event.QuestionStartedEvent(
                sessionId, questionId, NOW.plusSeconds(30), 30, NOW));
        projector.on(new io.quizchef.session.domain.event.QuestionClosedEvent(sessionId, questionId, NOW));
        projector.on(new io.quizchef.session.domain.event.AnswerRevealedEvent(
                sessionId, questionId, java.util.Set.of(UUID.randomUUID()), NOW));
        projector.on(new io.quizchef.session.domain.event.LeaderboardUpdatedEvent(
                sessionId, java.util.List.of(), NOW));

        assertThat(publisher.broadcasts)
                .extracting(ProtocolMessage::type)
                .containsExactly(ProtocolMessageType.QUESTION_STARTED,
                        ProtocolMessageType.QUESTION_CLOSED,
                        ProtocolMessageType.ANSWER_REVEALED,
                        ProtocolMessageType.LEADERBOARD_UPDATED);
        assertThat(((io.quizchef.websocket.api.event.QuestionStartedPayload)
                publisher.broadcasts.getFirst().payload()).durationSeconds()).isEqualTo(30);
    }

    @Test
    void leaderboardBroadcastCarriesNoStandings() {
        // The session topic reaches every participant device, and standings
        // are role-scoped reads (live-event privacy): even when the domain
        // event carries ranked entries, the broadcast is a pure notification.
        projector.on(new io.quizchef.session.domain.event.LeaderboardUpdatedEvent(
                sessionId,
                java.util.List.of(new io.quizchef.session.domain.LeaderboardEntry(
                        UUID.randomUUID(), "Ann", 750, 1)),
                NOW));

        assertThat(publisher.broadcasts).hasSize(1);
        assertThat(((io.quizchef.websocket.api.event.LeaderboardPayload)
                publisher.broadcasts.getFirst().payload()).entries()).isEmpty();
    }

    @Test
    void sendsTheAnswerAcknowledgementToTheSubmittingParticipantOnly() {
        UUID questionId = UUID.randomUUID();
        projector.on(new io.quizchef.session.domain.event.AnswerSubmittedEvent(
                sessionId, participantId, questionId, NOW));

        assertThat(publisher.participantMessages).containsOnlyKeys(participantId);
        assertThat(publisher.participantMessages.get(participantId).type())
                .isEqualTo(ProtocolMessageType.ANSWER_ACCEPTED);
    }

    @Test
    void broadcastsAnswerProgressWithoutNamingTheParticipant() {
        // The session-wide companion of an accepted answer is a pure
        // "counts moved" notification: the authoritative numbers live in
        // the host-only progress read, and the broadcast reaches every
        // participant device — so it must not say who answered.
        UUID questionId = UUID.randomUUID();
        projector.on(new io.quizchef.session.domain.event.AnswerSubmittedEvent(
                sessionId, participantId, questionId, NOW));

        assertThat(publisher.broadcasts).hasSize(1);
        ProtocolMessage broadcast = publisher.broadcasts.getFirst();
        assertThat(broadcast.type()).isEqualTo(ProtocolMessageType.ANSWER_PROGRESS);
        assertThat(broadcast.payload())
                .isEqualTo(new io.quizchef.websocket.api.event.QuestionPayload(questionId));
    }

    private static final class CapturingPublisher implements RealtimePublisher {

        private final List<ProtocolMessage> broadcasts = new ArrayList<>();
        private final java.util.Map<UUID, ProtocolMessage> participantMessages = new java.util.HashMap<>();

        @Override
        public void publish(ProtocolMessage message) {
            broadcasts.add(message);
        }

        @Override
        public void publishToParticipant(UUID participantId, ProtocolMessage message) {
            participantMessages.put(participantId, message);
        }

        @Override
        public void publishToHost(UUID sessionId, ProtocolMessage message) {
            throw new AssertionError("no host messages expected in this PR");
        }
    }
}
