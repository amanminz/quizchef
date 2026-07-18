package io.quizchef.platform.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.quizchef.session.domain.event.AnswerSubmittedEvent;
import io.quizchef.session.domain.event.ParticipantReconnectedEvent;
import io.quizchef.session.domain.event.QuestionClosedEvent;
import io.quizchef.session.domain.event.QuestionStartedEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GameplayMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final GameplayMetrics metrics = new GameplayMetrics(registry);
    private final UUID sessionId = UUID.randomUUID();
    private final UUID questionId = UUID.randomUUID();

    @Test
    void countsAnswersSubmitted() {
        metrics.on(new AnswerSubmittedEvent(sessionId, UUID.randomUUID(), questionId, Instant.now()));

        assertThat(registry.counter("gameplay.answers_submitted").count()).isEqualTo(1.0);
    }

    @Test
    void recordsAnswerLatencyFromQuestionOpenToSubmission() {
        Instant opened = Instant.now();
        metrics.on(new QuestionStartedEvent(sessionId, questionId, opened.plusSeconds(20), 20, opened));

        metrics.on(new AnswerSubmittedEvent(sessionId, UUID.randomUUID(), questionId,
                opened.plus(Duration.ofSeconds(4))));

        assertThat(registry.get("gameplay.answer_latency").timer().count()).isEqualTo(1L);
        assertThat(registry.get("gameplay.answer_latency").timer().totalTime(java.util.concurrent.TimeUnit.SECONDS))
                .isCloseTo(4.0, org.assertj.core.data.Offset.offset(0.5));
    }

    @Test
    void evictsQuestionTimingOnClose() {
        Instant opened = Instant.now();
        metrics.on(new QuestionStartedEvent(sessionId, questionId, opened.plusSeconds(20), 20, opened));
        metrics.on(new QuestionClosedEvent(sessionId, questionId, opened.plusSeconds(20)));

        metrics.on(new AnswerSubmittedEvent(sessionId, UUID.randomUUID(), questionId, opened.plusSeconds(25)));

        assertThat(registry.find("gameplay.answer_latency").timer()).isNull();
    }

    @Test
    void countsReconnectRecovery() {
        metrics.on(new ParticipantReconnectedEvent(sessionId, UUID.randomUUID(), Instant.now()));

        assertThat(registry.counter("gameplay.reconnect_recovery").count()).isEqualTo(1.0);
    }
}
