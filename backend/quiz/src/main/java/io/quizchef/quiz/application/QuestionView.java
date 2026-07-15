package io.quizchef.quiz.application;

import io.quizchef.quiz.domain.BibleReference;
import io.quizchef.quiz.domain.Difficulty;
import io.quizchef.quiz.domain.LanguageCode;
import io.quizchef.quiz.domain.MediaReference;
import io.quizchef.quiz.domain.Option;
import io.quizchef.quiz.domain.OptionLocalization;
import io.quizchef.quiz.domain.Question;
import io.quizchef.quiz.domain.QuestionLocalization;
import io.quizchef.quiz.domain.QuestionSource;
import io.quizchef.quiz.domain.QuestionState;
import io.quizchef.quiz.domain.QuestionType;
import io.quizchef.quiz.domain.Tag;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * The application layer's read model of a question: metadata, structure,
 * every localization with its option texts, references, and tags. Quiz
 * references are deliberately absent — questions do not know where they
 * are used.
 */
public record QuestionView(
        UUID id,
        UUID ownerIdentityId,
        LanguageCode defaultLanguage,
        QuestionState state,
        QuestionType questionType,
        Difficulty difficulty,
        QuestionSource source,
        long version,
        List<Option> options,
        List<LocalizationView> localizations,
        List<BibleReference> bibleReferences,
        List<MediaReference> mediaReferences,
        List<TagView> tags,
        Instant createdAt,
        Instant updatedAt
) {

    public record LocalizationView(
            LanguageCode languageCode,
            String title,
            String prompt,
            String explanation,
            List<OptionLocalization> optionTexts
    ) {
    }

    public record TagView(UUID id, String name) {
    }

    public static QuestionView of(Question question, List<Tag> tags) {
        List<LocalizationView> localizations = question.localizations().stream()
                .map(localization -> localizationView(question, localization))
                .toList();
        List<TagView> tagViews = tags.stream()
                .sorted(Comparator.comparing(Tag::getName))
                .map(tag -> new TagView(tag.getId(), tag.getName()))
                .toList();
        return new QuestionView(
                question.getId(),
                question.getOwnerIdentity().identityId(),
                question.getDefaultLanguage(),
                question.getState(),
                question.getQuestionType(),
                question.getDifficulty(),
                question.getSource(),
                question.getVersion(),
                question.options(),
                localizations,
                question.bibleReferences(),
                question.mediaReferences(),
                tagViews,
                question.getCreatedAt(),
                question.getUpdatedAt());
    }

    private static LocalizationView localizationView(Question question,
                                                     QuestionLocalization localization) {
        return new LocalizationView(
                localization.languageCode(),
                localization.title(),
                localization.prompt(),
                localization.explanation(),
                question.optionLocalizations(localization.languageCode()));
    }
}
