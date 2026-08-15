package io.quizchef.session.domain;

import io.quizchef.quiz.domain.LanguageCode;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Embeddable;
import java.util.Objects;

/**
 * A corrected question prompt in one language, inside a
 * {@link SessionQuestionCorrection}.
 */
@Embeddable
public record CorrectedPrompt(
        @Embedded
        @AttributeOverride(name = "value", column = @Column(name = "language_code", nullable = false, length = 20))
        LanguageCode languageCode,
        @Column(name = "prompt", nullable = false, length = 2000)
        String prompt
) {

    public CorrectedPrompt {
        Objects.requireNonNull(languageCode, "languageCode must not be null");
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("prompt must not be blank");
        }
        prompt = prompt.strip();
    }
}
