package io.quizchef.quiz.application;

import io.quizchef.quiz.domain.Difficulty;
import io.quizchef.quiz.domain.LanguageCode;
import io.quizchef.quiz.domain.Question;
import io.quizchef.quiz.domain.QuestionLocalization;
import io.quizchef.quiz.domain.QuestionState;
import io.quizchef.quiz.domain.QuestionType;
import io.quizchef.quiz.domain.Tag;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A question as the library browse/picker presents it — a preview, not the
 * full editable representation ({@link QuestionView}). Built for the
 * Question Picker (RFC-009): enough to render a card and decide whether to
 * attach it, nothing that requires loading every localization's option
 * texts.
 */
public record QuestionSummaryView(
        UUID id,
        LanguageCode defaultLanguage,
        QuestionState state,
        QuestionType questionType,
        Difficulty difficulty,
        String title,
        Set<LanguageCode> availableLanguages,
        List<QuestionView.TagView> tags,
        long version,
        Instant updatedAt
) {

    static QuestionSummaryView of(Question question, List<Tag> tags) {
        List<QuestionView.TagView> tagViews = tags.stream()
                .sorted(Comparator.comparing(Tag::getName))
                .map(tag -> new QuestionView.TagView(tag.getId(), tag.getName()))
                .toList();
        Set<LanguageCode> availableLanguages = question.localizations().stream()
                .map(QuestionLocalization::languageCode)
                .collect(Collectors.toUnmodifiableSet());
        return new QuestionSummaryView(
                question.getId(),
                question.getDefaultLanguage(),
                question.getState(),
                question.getQuestionType(),
                question.getDifficulty(),
                question.defaultLocalization().title(),
                availableLanguages,
                tagViews,
                question.getVersion(),
                question.getUpdatedAt());
    }
}
