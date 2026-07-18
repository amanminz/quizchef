package io.quizchef.session.infrastructure;

import io.quizchef.common.correlation.CorrelationKeys;
import io.quizchef.session.domain.LeaderboardService;
import io.quizchef.session.domain.ScoringPolicy;
import io.quizchef.session.domain.ScoringService;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Wires the framework-independent gameplay domain services as beans and
 * provides the scheduler that closes expired questions. The domain classes
 * stay Spring-free; this configuration is where they become injectable.
 */
@Configuration
public class SessionGameplayConfiguration {

    @Bean
    public ScoringService scoringService() {
        return new ScoringService();
    }

    @Bean
    public LeaderboardService leaderboardService() {
        return new LeaderboardService();
    }

    /**
     * The active scoring scheme. A single bean today; a per-session or
     * per-organization policy can replace it without touching the engine.
     */
    @Bean
    public ScoringPolicy scoringPolicy() {
        return ScoringPolicy.classic();
    }

    /**
     * Expired-question closes fire on this pool's own threads, off any HTTP
     * request — so there is no correlation id to inherit via MDC (RFC-010).
     * A synthetic per-firing id keeps {@code closeIfExpired}'s logs
     * searchable as a group without pretending they belong to a request.
     */
    @Bean
    public TaskScheduler gameplayTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("question-timer-");
        scheduler.setTaskDecorator(timerCorrelationDecorator());
        scheduler.initialize();
        return scheduler;
    }

    private TaskDecorator timerCorrelationDecorator() {
        return runnable -> () -> {
            MDC.put(CorrelationKeys.CORRELATION_ID_MDC_KEY, "timer-" + UUID.randomUUID());
            try {
                runnable.run();
            } finally {
                MDC.clear();
            }
        };
    }
}
