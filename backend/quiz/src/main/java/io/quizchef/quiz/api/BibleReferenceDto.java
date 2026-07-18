package io.quizchef.quiz.api;

import io.quizchef.quiz.domain.BibleReference;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A scripture reference — deliberately not localized: the canonical
 * reference is language independent.
 */
public record BibleReferenceDto(
        @Schema(example = "Exodus") @NotBlank @Size(max = 50) String book,
        @Schema(example = "3") @NotNull @Min(1) @Max(150) Integer chapter,
        @Schema(example = "1") @NotNull @Min(1) @Max(200) Integer verseStart,
        @Schema(example = "10", description = "Omit for a single verse") @Min(1) @Max(200) Integer verseEnd,
        @Schema(example = "ESV") @Size(max = 20) String translation
) {

    /**
     * A structural check at the API boundary (Phase 3 PR #3 / RFC-011) —
     * Bean Validation recognizes any {@code isXxx()} boolean method as a
     * constrained property, record or not.
     */
    @AssertTrue(message = "verseEnd must not be before verseStart")
    public boolean isVerseRangeValid() {
        return verseEnd == null || verseStart == null || verseEnd >= verseStart;
    }

    BibleReference toReference() {
        return new BibleReference(book, chapter, verseStart, verseEnd, translation);
    }

    static BibleReferenceDto from(BibleReference reference) {
        return new BibleReferenceDto(reference.book(), reference.chapter(),
                reference.verseStart(), reference.verseEnd(), reference.translation());
    }
}
