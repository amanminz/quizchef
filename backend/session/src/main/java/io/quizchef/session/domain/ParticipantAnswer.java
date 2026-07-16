package io.quizchef.session.domain;

import io.quizchef.quiz.domain.LanguageCode;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embedded;
import jakarta.persistence.Embeddable;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * One answer a participant gave to one question — a model only. It records
 * what was chosen and how it scored; it does not compute the score
 * (pointsAwarded is supplied) and there is no submission flow here. Answers
 * belong to the {@link Participant} (never their own aggregate), so a
 * participant's history and cached score stay one consistency boundary
 * (ADR-003).
 *
 * <p>Response time is stored as milliseconds for schema simplicity and
 * exposed as {@link Duration}. {@code answeredLanguage} records which
 * translation the participant actually played in — future analytics on
 * which localizations get used.
 */
@Embeddable
public record ParticipantAnswer(
        UUID questionId,
        @Convert(converter = SelectedOptionsConverter.class)
        @Column(name = "selected_option_ids", nullable = false)
        Set<UUID> selectedOptionIds,
        @Embedded
        @AttributeOverride(name = "value", column = @Column(name = "answered_language", length = 20))
        LanguageCode answeredLanguage,
        Instant submittedAt,
        long responseTimeMillis,
        int pointsAwarded
) {

    public ParticipantAnswer {
        Objects.requireNonNull(questionId, "questionId must not be null");
        Objects.requireNonNull(answeredLanguage, "answeredLanguage must not be null");
        Objects.requireNonNull(submittedAt, "submittedAt must not be null");
        selectedOptionIds = selectedOptionIds == null ? Set.of() : Set.copyOf(selectedOptionIds);
        if (responseTimeMillis < 0) {
            throw new IllegalArgumentException("responseTimeMillis must not be negative");
        }
        if (pointsAwarded < 0) {
            throw new IllegalArgumentException("pointsAwarded must not be negative");
        }
    }

    public Duration responseTime() {
        return Duration.ofMillis(responseTimeMillis);
    }
}
