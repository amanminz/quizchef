package io.quizchef.quiz.api;

import io.quizchef.quiz.application.UpdateQuestionCommand;
import io.quizchef.quiz.domain.Difficulty;
import io.quizchef.quiz.domain.QuestionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * Replaces a draft question's full editable representation — this is a
 * true PUT: every field is the complete new value. Options keep their ids
 * to preserve translations (new options carry fresh, client-generated
 * ids); the localizations must include the default language. The version
 * must be the one last read — stale versions are rejected with 409.
 */
public record UpdateQuestionRequest(
        @Schema(example = "0", description = "The version returned by the last read of this question")
        @NotNull Long version,
        @Schema(description = "The draft's new question type; omit to keep the current one")
        QuestionType questionType,
        @Schema(example = "en", description = "The draft's new default language (must be fully "
                + "localized in this request); omit to keep the current one")
        @Size(max = 20) String defaultLanguage,
        @NotNull Difficulty difficulty,
        @NotNull @Size(min = 1, max = 20) @Valid List<UpdateOptionDto> options,
        @NotNull @Size(min = 1, max = 50) @Valid List<QuestionLocalizationDto> localizations,
        @NotNull @Size(max = 20) @Valid List<BibleReferenceDto> bibleReferences,
        @NotNull @Size(max = 20) @Valid List<MediaReferenceDto> mediaReferences,
        @NotNull @Size(max = 20) List<@NotBlank @Size(max = 30) String> tags
) {

    public record UpdateOptionDto(
            @NotNull UUID id,
            @NotNull Boolean correct,
            @Schema(example = "1") @NotNull @Min(1) @Max(1000) Integer displayOrder
    ) {
    }

    UpdateQuestionCommand toCommand(UUID questionId) {
        return new UpdateQuestionCommand(
                questionId,
                version,
                questionType,
                defaultLanguage,
                difficulty,
                options.stream()
                        .map(option -> new UpdateQuestionCommand.UpdateQuestionOptionCommand(
                                option.id(), option.correct(), option.displayOrder()))
                        .toList(),
                localizations.stream().map(QuestionLocalizationDto::toCommand).toList(),
                bibleReferences.stream().map(BibleReferenceDto::toReference).toList(),
                mediaReferences.stream().map(MediaReferenceDto::toReference).toList(),
                tags);
    }
}
