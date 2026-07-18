package io.quizchef.platform.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.quizchef.session.domain.event.AnswerSubmittedEvent;
import io.quizchef.session.domain.event.ParticipantReconnectedEvent;
import io.quizchef.session.domain.event.QuestionClosedEvent;
import io.quizchef.session.domain.event.QuestionStartedEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Gameplay operational metrics. Answer latency is computed from the gap
 * between {@link QuestionStartedEvent} and each {@link AnswerSubmittedEvent}
 * for the same question — tracked transiently, keyed by session+question,
 * and evicted on {@link QuestionClosedEvent} — without adding a timing field
 * to any domain event (RFC-010).
 */
@Component
public class GameplayMetrics {

    private record QuestionKey(UUID sessionId, UUID questionId) {
    }

    private final MeterRegistry meterRegistry;
    private final Map<QuestionKey, Instant> questionOpenedAt = new ConcurrentHashMap<>();

    public GameplayMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @EventListener
    void on(QuestionStartedEvent event) {
        questionOpenedAt.put(new QuestionKey(event.sessionId(), event.questionId()), event.occurredAt());
    }

    @EventListener
    void on(AnswerSubmittedEvent event) {
        meterRegistry.counter("gameplay.answers_submitted").increment();

        Instant openedAt = questionOpenedAt.get(new QuestionKey(event.sessionId(), event.questionId()));
        if (openedAt != null) {
            Timer.builder("gameplay.answer_latency").register(meterRegistry)
                    .record(Duration.between(openedAt, event.occurredAt()));
        }
    }

    @EventListener
    void on(QuestionClosedEvent event) {
        questionOpenedAt.remove(new QuestionKey(event.sessionId(), event.questionId()));
    }

    @EventListener
    void on(ParticipantReconnectedEvent event) {
        meterRegistry.counter("gameplay.reconnect_recovery").increment();
    }
}
