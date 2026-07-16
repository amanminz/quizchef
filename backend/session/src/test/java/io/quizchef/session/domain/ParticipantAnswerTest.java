package io.quizchef.session.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import io.quizchef.quiz.domain.LanguageCode;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ParticipantAnswerTest {

    private static final LanguageCode EN = LanguageCode.of("en");
    private static final Instant NOW = Instant.parse("2026-07-16T10:00:00Z");

    private static ParticipantAnswer answer(long responseMillis, int points) {
        return new ParticipantAnswer(UUID.randomUUID(), Set.of(UUID.randomUUID()), EN, NOW,
                responseMillis, points);
    }

    @Test
    void recordsSelectionLanguageAndScore() {
        UUID option = UUID.randomUUID();
        ParticipantAnswer answer = new ParticipantAnswer(
                UUID.randomUUID(), Set.of(option), EN, NOW, 1500, 800);

        assertThat(answer.selectedOptionIds()).containsExactly(option);
        assertThat(answer.answeredLanguage()).isEqualTo(EN);
        assertThat(answer.responseTime()).isEqualTo(Duration.ofMillis(1500));
        assertThat(answer.pointsAwarded()).isEqualTo(800);
    }

    @Test
    void requiresQuestionLanguageAndSubmittedAt() {
        assertThatNullPointerException().isThrownBy(() ->
                new ParticipantAnswer(null, Set.of(), EN, NOW, 0, 0));
        assertThatNullPointerException().isThrownBy(() ->
                new ParticipantAnswer(UUID.randomUUID(), Set.of(), null, NOW, 0, 0));
        assertThatNullPointerException().isThrownBy(() ->
                new ParticipantAnswer(UUID.randomUUID(), Set.of(), EN, null, 0, 0));
    }

    @Test
    void rejectsNegativeResponseTimeAndPoints() {
        assertThatIllegalArgumentException().isThrownBy(() -> answer(-1, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> answer(0, -1));
    }
}
