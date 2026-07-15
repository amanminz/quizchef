package io.quizchef.quiz.domain;

import jakarta.persistence.Embeddable;
import java.util.Objects;
import java.util.UUID;

/**
 * One answer option of a question: identity, correctness, and position —
 * deliberately language neutral. Identity matters (participants pick an
 * option id during gameplay), correctness belongs here and never to a
 * translation, and the displayable text lives in
 * {@link OptionLocalization}.
 */
@Embeddable
public record Option(
        UUID id,
        boolean correct,
        int displayOrder
) {

    public Option {
        Objects.requireNonNull(id, "id must not be null");
        if (displayOrder < 1) {
            throw new IllegalArgumentException("displayOrder must be positive");
        }
    }

    public static Option of(boolean correct, int displayOrder) {
        return new Option(UUID.randomUUID(), correct, displayOrder);
    }

    public OptionLocalization localized(LanguageCode languageCode, String text) {
        return new OptionLocalization(id, languageCode, text);
    }
}
