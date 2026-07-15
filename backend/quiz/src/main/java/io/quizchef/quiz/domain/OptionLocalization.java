package io.quizchef.quiz.domain;

import jakarta.persistence.Embeddable;
import java.util.Objects;
import java.util.UUID;

/**
 * One option's displayable text in one language. Correctness stays on
 * {@link Option} — never on translated text — so gameplay and scoring
 * remain language independent.
 */
@Embeddable
public record OptionLocalization(
        UUID optionId,
        LanguageCode languageCode,
        String text
) {

    public OptionLocalization {
        Objects.requireNonNull(optionId, "optionId must not be null");
        Objects.requireNonNull(languageCode, "languageCode must not be null");
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("option text must not be blank");
        }
        text = text.strip();
    }
}
