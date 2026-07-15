package io.quizchef.quiz.domain;

import jakarta.persistence.Embeddable;
import java.util.Objects;

/**
 * A question's displayable text in one language. Lives inside the
 * Question aggregate — the root guarantees one localization per language,
 * that the default language is always present, and that every stored
 * language also localizes every option.
 */
@Embeddable
public record QuestionLocalization(
        LanguageCode languageCode,
        String title,
        String prompt,
        String explanation
) {

    public QuestionLocalization {
        Objects.requireNonNull(languageCode, "languageCode must not be null");
        title = requireText(title, "title");
        prompt = requireText(prompt, "prompt");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.strip();
    }
}
