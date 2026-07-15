package io.quizchef.quiz.api;

import io.quizchef.quiz.application.QuestionView;
import io.quizchef.quiz.domain.Difficulty;
import io.quizchef.quiz.domain.Option;
import io.quizchef.quiz.domain.QuestionSource;
import io.quizchef.quiz.domain.QuestionState;
import io.quizchef.quiz.domain.QuestionType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A question as the library API presents it: metadata, structure, every
 * localization with its option texts, references, and tags. Quiz
 * references are deliberately absent — questions do not know where they
 * are used.
 */
public record QuestionResponse(
        UUID id,
        UUID ownerIdentityId,
        @Schema(example = "en") String defaultLanguage,
        QuestionState state,
        QuestionType questionType,
        Difficulty difficulty,
        QuestionSource source,
        @Schema(description = "Send this back with PUT; stale versions are rejected with 409")
        long version,
        List<OptionDto> options,
        List<QuestionLocalizationDto> localizations,
        List<BibleReferenceDto> bibleReferences,
        List<MediaReferenceDto> mediaReferences,
        List<TagDto> tags,
        Instant createdAt,
        Instant updatedAt
) {

    public record OptionDto(UUID id, boolean correct, int displayOrder) {

        static OptionDto from(Option option) {
            return new OptionDto(option.id(), option.correct(), option.displayOrder());
        }
    }

    public record TagDto(UUID id, @Schema(example = "exodus") String name) {
    }

    static QuestionResponse from(QuestionView view) {
        return new QuestionResponse(
                view.id(),
                view.ownerIdentityId(),
                view.defaultLanguage().value(),
                view.state(),
                view.questionType(),
                view.difficulty(),
                view.source(),
                view.version(),
                view.options().stream().map(OptionDto::from).toList(),
                view.localizations().stream().map(QuestionLocalizationDto::from).toList(),
                view.bibleReferences().stream().map(BibleReferenceDto::from).toList(),
                view.mediaReferences().stream().map(MediaReferenceDto::from).toList(),
                view.tags().stream().map(tag -> new TagDto(tag.id(), tag.name())).toList(),
                view.createdAt(),
                view.updatedAt());
    }
}
