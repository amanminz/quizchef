package io.quizchef.session.domain;

import io.quizchef.quiz.domain.LanguageCode;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Embeddable;
import java.util.Objects;
import java.util.UUID;

/**
 * One option's corrected wording in one language, inside a
 * {@link SessionQuestionCorrection}. The option's identity is the authored
 * one — a correction rewords options, it never invents or drops them.
 */
@Embeddable
public record CorrectedOptionText(
        @Embedded
        @AttributeOverride(name = "value", column = @Column(name = "language_code", nullable = false, length = 20))
        LanguageCode languageCode,
        @Column(name = "option_id", nullable = false)
        UUID optionId,
        @Column(name = "text", nullable = false, length = 1000)
        String text
) {

    public CorrectedOptionText {
        Objects.requireNonNull(languageCode, "languageCode must not be null");
        Objects.requireNonNull(optionId, "optionId must not be null");
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("option text must not be blank");
        }
        text = text.strip();
    }
}
