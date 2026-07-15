package io.quizchef.quiz.domain;

import jakarta.persistence.Embeddable;
import java.util.Objects;
import java.util.UUID;

/**
 * One answer option of a question. Identity matters (participants pick an
 * option id during gameplay), but options live and die with their question.
 */
@Embeddable
public record Option(
        UUID id,
        String text,
        boolean correct,
        int displayOrder
) {

    public Option {
        Objects.requireNonNull(id, "id must not be null");
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("option text must not be blank");
        }
        text = text.strip();
        if (displayOrder < 1) {
            throw new IllegalArgumentException("displayOrder must be positive");
        }
    }

    public static Option of(String text, boolean correct, int displayOrder) {
        return new Option(UUID.randomUUID(), text, correct, displayOrder);
    }
}
