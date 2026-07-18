package io.quizchef.platform.logging;

import static org.assertj.core.api.Assertions.assertThat;

import io.quizchef.session.domain.event.AnswerSubmittedEvent;
import io.quizchef.session.domain.event.ParticipantDisconnectedEvent;
import io.quizchef.session.domain.event.ParticipantJoinedEvent;
import io.quizchef.session.domain.event.ParticipantReconnectedEvent;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ParticipantEventLoggerTest {

    private final ParticipantEventLogger logger = new ParticipantEventLogger();
    private final UUID sessionId = UUID.randomUUID();
    private final UUID participantId = UUID.randomUUID();

    @Test
    void logsJoined() {
        try (LogCapture capture = new LogCapture(ParticipantEventLogger.class)) {
            logger.on(new ParticipantJoinedEvent(sessionId, participantId, Instant.now()));

            assertThat(capture.messages()).anyMatch(m -> m.contains("participant.joined"));
        }
    }

    @Test
    void logsReconnected() {
        try (LogCapture capture = new LogCapture(ParticipantEventLogger.class)) {
            logger.on(new ParticipantReconnectedEvent(sessionId, participantId, Instant.now()));

            assertThat(capture.messages()).anyMatch(m -> m.contains("participant.reconnected"));
        }
    }

    @Test
    void logsDisconnected() {
        try (LogCapture capture = new LogCapture(ParticipantEventLogger.class)) {
            logger.on(new ParticipantDisconnectedEvent(sessionId, participantId, Instant.now()));

            assertThat(capture.messages()).anyMatch(m -> m.contains("participant.disconnected"));
        }
    }

    @Test
    void logsAnswerSubmittedWithoutCorrectnessOrScore() {
        UUID questionId = UUID.randomUUID();
        try (LogCapture capture = new LogCapture(ParticipantEventLogger.class)) {
            logger.on(new AnswerSubmittedEvent(sessionId, participantId, questionId, Instant.now()));

            assertThat(capture.messages()).anyMatch(m -> m.contains("participant.answer_submitted")
                    && m.contains(questionId.toString()));
        }
    }
}
