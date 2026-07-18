package io.quizchef.platform.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.quizchef.session.domain.event.ParticipantJoinedEvent;
import io.quizchef.session.domain.event.SessionCreatedEvent;
import io.quizchef.session.domain.event.SessionFinishedEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Session-level operational metrics, derived entirely from existing domain
 * events — no domain event schema change was needed to compute duration or
 * participant count (RFC-010). Both are tracked transiently, keyed by
 * sessionId, and evicted the moment a session finishes: bounded by
 * concurrently live sessions (small at church scale), reset on restart —
 * acceptable for v1 metrics, not a source of truth.
 */
@Component
public class SessionMetrics {

    private final MeterRegistry meterRegistry;
    private final AtomicInteger activeSessions;
    private final Map<UUID, Instant> startedAt = new ConcurrentHashMap<>();
    private final Map<UUID, AtomicInteger> participantCounts = new ConcurrentHashMap<>();

    public SessionMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.activeSessions = meterRegistry.gauge("session.active", new AtomicInteger(0));
    }

    @EventListener
    void on(SessionCreatedEvent event) {
        activeSessions.incrementAndGet();
        startedAt.put(event.sessionId(), event.occurredAt());
        participantCounts.put(event.sessionId(), new AtomicInteger(0));
    }

    @EventListener
    void on(ParticipantJoinedEvent event) {
        AtomicInteger count = participantCounts.get(event.sessionId());
        if (count != null) {
            count.incrementAndGet();
        }
    }

    @EventListener
    void on(SessionFinishedEvent event) {
        activeSessions.decrementAndGet();
        meterRegistry.counter("session.completed").increment();

        Instant start = startedAt.remove(event.sessionId());
        if (start != null) {
            Timer.builder("session.duration").register(meterRegistry)
                    .record(Duration.between(start, event.occurredAt()));
        }

        AtomicInteger participants = participantCounts.remove(event.sessionId());
        if (participants != null) {
            meterRegistry.summary("session.participants").record(participants.get());
        }
    }
}
