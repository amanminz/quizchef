package io.quizchef.common.configuration;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The single source of time for the application.
 *
 * <p>Components that need "now" inject {@link Clock} instead of calling the
 * system clock directly, so time is controllable in tests.
 */
@Configuration
public class ClockConfiguration {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
