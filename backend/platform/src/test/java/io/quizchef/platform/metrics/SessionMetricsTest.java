package io.quizchef.platform.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.quizchef.identity.domain.IdentityReference;
import io.quizchef.identity.domain.IdentityType;
import io.quizchef.session.domain.event.ParticipantJoinedEvent;
import io.quizchef.session.domain.event.SessionCreatedEvent;
import io.quizchef.session.domain.event.SessionFinishedEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SessionMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final SessionMetrics metrics = new SessionMetrics(registry);
    private final IdentityReference host = new IdentityReference(UUID.randomUUID(), IdentityType.REGISTERED);

    @Test
    void tracksActiveSessionsAcrossCreateAndFinish() {
        UUID sessionId = UUID.randomUUID();
        metrics.on(new SessionCreatedEvent(sessionId, host, UUID.randomUUID(), Instant.now()));

        assertThat(registry.get("session.active").gauge().value()).isEqualTo(1.0);

        metrics.on(new SessionFinishedEvent(sessionId, Instant.now()));

        assertThat(registry.get("session.active").gauge().value()).isEqualTo(0.0);
    }

    @Test
    void countsCompletedSessions() {
        UUID sessionId = UUID.randomUUID();
        metrics.on(new SessionCreatedEvent(sessionId, host, UUID.randomUUID(), Instant.now()));
        metrics.on(new SessionFinishedEvent(sessionId, Instant.now()));

        assertThat(registry.counter("session.completed").count()).isEqualTo(1.0);
    }

    @Test
    void recordsSessionDuration() {
        UUID sessionId = UUID.randomUUID();
        Instant start = Instant.now();
        metrics.on(new SessionCreatedEvent(sessionId, host, UUID.randomUUID(), start));

        metrics.on(new SessionFinishedEvent(sessionId, start.plus(Duration.ofMinutes(5))));

        assertThat(registry.get("session.duration").timer().count()).isEqualTo(1L);
        assertThat(registry.get("session.duration").timer().totalTime(java.util.concurrent.TimeUnit.SECONDS))
                .isCloseTo(300.0, org.assertj.core.data.Offset.offset(1.0));
    }

    @Test
    void recordsParticipantsPerSession() {
        UUID sessionId = UUID.randomUUID();
        metrics.on(new SessionCreatedEvent(sessionId, host, UUID.randomUUID(), Instant.now()));
        metrics.on(new ParticipantJoinedEvent(sessionId, UUID.randomUUID(), Instant.now()));
        metrics.on(new ParticipantJoinedEvent(sessionId, UUID.randomUUID(), Instant.now()));

        metrics.on(new SessionFinishedEvent(sessionId, Instant.now()));

        assertThat(registry.get("session.participants").summary().max()).isEqualTo(2.0);
    }
}
