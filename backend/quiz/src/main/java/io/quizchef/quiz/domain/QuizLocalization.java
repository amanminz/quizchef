package io.quizchef.quiz.domain;

import jakarta.persistence.Embeddable;
import java.util.Objects;

/**
 * A quiz's displayable text in one language. Lives inside the Quiz
 * aggregate — the root guarantees one localization per language and that
 * the default language is always present.
 */
@Embeddable
public record QuizLocalization(
        LanguageCode languageCode,
        String title,
        String description
) {

    public QuizLocalization {
        Objects.requireNonNull(languageCode, "languageCode must not be null");
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        title = title.strip();
    }
}
