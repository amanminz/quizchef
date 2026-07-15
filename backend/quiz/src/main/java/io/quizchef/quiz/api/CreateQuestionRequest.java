package io.quizchef.quiz.api;

import io.quizchef.quiz.application.CreateQuestionCommand;
import io.quizchef.quiz.domain.Difficulty;
import io.quizchef.quiz.domain.QuestionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Creates a draft question in its default language. Option ids are
 * assigned by the server and returned in the response; translations are
 * added later via PUT. The caller becomes the owner.
 */
public record CreateQuestionRequest(
        @Schema(example = "en", description = "BCP-47 tag; the content is authored in this language")
        @NotBlank String defaultLanguage,
        @NotNull QuestionType questionType,
        @NotNull Difficulty difficulty,
        @NotNull @Valid QuestionContentDto localization,
        @NotNull @Size(min = 1) @Valid List<CreateOptionDto> options,
        @Valid List<BibleReferenceDto> bibleReferences,
        @Valid List<MediaReferenceDto> mediaReferences,
        @Schema(example = "[\"exodus\", \"moses\"]", description = "Resolved or created by name; normalized to lowercase")
        List<String> tags
) {

    /**
     * The default-language text of the question itself; option texts ride
     * on the options at creation time.
     */
    public record QuestionContentDto(
            @Schema(example = "Exodus leader") @NotBlank @Size(max = 200) String title,
            @Schema(example = "Who led Israel out of Egypt?") @NotBlank @Size(max = 4000) String prompt,
            @Schema(example = "See Exodus 3.") @Size(max = 4000) String explanation
    ) {
    }

    public record CreateOptionDto(
            @Schema(example = "Moses") @NotBlank @Size(max = 500) String text,
            @NotNull Boolean correct,
            @Schema(example = "1") @NotNull @Min(1) Integer displayOrder
    ) {
    }

    CreateQuestionCommand toCommand() {
        return new CreateQuestionCommand(
                defaultLanguage,
                questionType,
                difficulty,
                localization.title(),
                localization.prompt(),
                localization.explanation(),
                options.stream()
                        .map(option -> new CreateQuestionCommand.CreateQuestionOptionCommand(
                                option.text(), option.correct(), option.displayOrder()))
                        .toList(),
                bibleReferences == null ? null
                        : bibleReferences.stream().map(BibleReferenceDto::toReference).toList(),
                mediaReferences == null ? null
                        : mediaReferences.stream().map(MediaReferenceDto::toReference).toList(),
                tags);
    }
}
