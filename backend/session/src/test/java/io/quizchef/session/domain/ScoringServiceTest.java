package io.quizchef.session.domain;

import static org.assertj.core.api.Assertions.assertThat;

import io.quizchef.quiz.domain.Difficulty;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class ScoringServiceTest {

    private static final ScoringPolicy CLASSIC = ScoringPolicy.classic();
    private static final Duration THIRTY_SECONDS = Duration.ofSeconds(30);

    private final ScoringService scoringService = new ScoringService();

    @Test
    void incorrectAnswersAlwaysScoreZero() {
        int score = scoringService.award(false, Duration.ZERO, THIRTY_SECONDS, Difficulty.HARD, CLASSIC);

        assertThat(score).isZero();
    }

    @Test
    void anInstantCorrectAnswerEarnsBasePlusFullSpeedBonus() {
        // response ~0 → remainingFraction ~1 → (500 + 500) × 1.0
        int score = scoringService.award(true, Duration.ZERO, THIRTY_SECONDS, Difficulty.EASY, CLASSIC);

        assertThat(score).isEqualTo(1000);
    }

    @Test
    void answeringAtTheBuzzerKeepsOnlyTheBase() {
        // response == duration → remainingFraction 0 → 500 × 1.0
        int score = scoringService.award(true, THIRTY_SECONDS, THIRTY_SECONDS, Difficulty.EASY, CLASSIC);

        assertThat(score).isEqualTo(500);
    }

    @Test
    void speedBonusDecaysLinearly() {
        // half the time used → remainingFraction 0.5 → (500 + 250) × 1.0
        int score = scoringService.award(true, Duration.ofSeconds(15), THIRTY_SECONDS, Difficulty.EASY, CLASSIC);

        assertThat(score).isEqualTo(750);
    }

    @Test
    void difficultyScalesTheWholeScore() {
        int easy = scoringService.award(true, Duration.ZERO, THIRTY_SECONDS, Difficulty.EASY, CLASSIC);
        int medium = scoringService.award(true, Duration.ZERO, THIRTY_SECONDS, Difficulty.MEDIUM, CLASSIC);
        int hard = scoringService.award(true, Duration.ZERO, THIRTY_SECONDS, Difficulty.HARD, CLASSIC);

        assertThat(easy).isEqualTo(1000);
        assertThat(medium).isEqualTo(1250);
        assertThat(hard).isEqualTo(1500);
    }

    @Test
    void anAnswerAfterTimeIsNeverNegativeAndKeepsNoBonus() {
        int score = scoringService.award(true, Duration.ofSeconds(45), THIRTY_SECONDS, Difficulty.MEDIUM, CLASSIC);

        assertThat(score).isEqualTo(Math.round(500 * 1.25));
    }
}
