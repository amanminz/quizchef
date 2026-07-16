package io.quizchef.session.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class QuestionTimerTest {

    private static final Instant START = Instant.parse("2026-07-16T10:00:00Z");

    @Test
    void computesEndFromStartAndDuration() {
        QuestionTimer timer = QuestionTimer.startingAt(START, Duration.ofSeconds(30));

        assertThat(timer.durationSeconds()).isEqualTo(30);
        assertThat(timer.endsAt()).isEqualTo(START.plusSeconds(30));
        assertThat(timer.duration()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void shouldRejectNonPositiveDuration() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new QuestionTimer(START, 0, START));
    }

    @Test
    void shouldRejectEndThatDoesNotMatchDuration() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new QuestionTimer(START, 30, START.plusSeconds(31)));
    }
}
