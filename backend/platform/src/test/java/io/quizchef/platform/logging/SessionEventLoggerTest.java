package io.quizchef.platform.logging;

import static org.assertj.core.api.Assertions.assertThat;

import io.quizchef.identity.domain.IdentityReference;
import io.quizchef.identity.domain.IdentityType;
import io.quizchef.session.domain.event.LobbyOpenedEvent;
import io.quizchef.session.domain.event.SessionCreatedEvent;
import io.quizchef.session.domain.event.SessionFinishedEvent;
import io.quizchef.session.domain.event.SessionStartedEvent;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SessionEventLoggerTest {

    private final SessionEventLogger logger = new SessionEventLogger();
    private final UUID sessionId = UUID.randomUUID();

    @Test
    void logsSessionCreated() {
        IdentityReference host = new IdentityReference(UUID.randomUUID(), IdentityType.REGISTERED);
        try (LogCapture capture = new LogCapture(SessionEventLogger.class)) {
            logger.on(new SessionCreatedEvent(sessionId, host, UUID.randomUUID(), Instant.now()));

            assertThat(capture.messages())
                    .anyMatch(m -> m.contains("session.created") && m.contains(sessionId.toString()));
        }
    }

    @Test
    void logsLobbyOpened() {
        try (LogCapture capture = new LogCapture(SessionEventLogger.class)) {
            logger.on(new LobbyOpenedEvent(sessionId, Instant.now()));

            assertThat(capture.messages()).anyMatch(m -> m.contains("session.lobby_opened"));
        }
    }

    @Test
    void logsSessionStarted() {
        try (LogCapture capture = new LogCapture(SessionEventLogger.class)) {
            logger.on(new SessionStartedEvent(sessionId, Instant.now()));

            assertThat(capture.messages()).anyMatch(m -> m.contains("session.started"));
        }
    }

    @Test
    void logsSessionFinished() {
        try (LogCapture capture = new LogCapture(SessionEventLogger.class)) {
            logger.on(new SessionFinishedEvent(sessionId, Instant.now()));

            assertThat(capture.messages()).anyMatch(m -> m.contains("session.finished"));
        }
    }
}
