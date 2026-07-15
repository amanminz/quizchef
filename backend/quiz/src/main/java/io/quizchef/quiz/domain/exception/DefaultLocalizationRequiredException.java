package io.quizchef.quiz.domain.exception;

import io.quizchef.common.exception.ConflictException;
import io.quizchef.quiz.domain.LanguageCode;

/**
 * The default language localization was asked to go away. Every quiz and
 * question keeps exactly one localization for its default language — it is
 * the fallback all display resolution ends at.
 */
public class DefaultLocalizationRequiredException extends ConflictException {

    public DefaultLocalizationRequiredException(LanguageCode defaultLanguage) {
        super("quiz.localization.default-required",
                "The default language localization (%s) cannot be removed".formatted(defaultLanguage.value()));
    }
}
