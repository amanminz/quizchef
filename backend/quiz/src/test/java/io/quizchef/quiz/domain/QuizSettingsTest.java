package io.quizchef.quiz.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class QuizSettingsTest {

    @Test
    void defaultsAreKahootStyle() {
        QuizSettings settings = QuizSettings.defaults();

        assertThat(settings.randomizeQuestionOrder()).isFalse();
        assertThat(settings.randomizeOptionOrder()).isFalse();
        assertThat(settings.questionTimeLimitSeconds()).isEqualTo(30);
        assertThat(settings.showLeaderboardAfterQuestion()).isTrue();
        assertThat(settings.showExplanationAfterQuestion()).isTrue();
    }

    @Test
    void shouldRejectTimeLimitOutsideAllowedRange() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new QuizSettings(false, false, 4, true, true));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new QuizSettings(false, false, 301, true, true));
    }

    @Test
    void shouldAcceptBoundaryTimeLimits() {
        assertThat(new QuizSettings(false, false, 5, true, true).questionTimeLimitSeconds()).isEqualTo(5);
        assertThat(new QuizSettings(false, false, 300, true, true).questionTimeLimitSeconds()).isEqualTo(300);
    }
}
