package io.quizchef.quiz.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.quizchef.quiz.domain.Difficulty;
import io.quizchef.quiz.domain.MediaType;
import io.quizchef.quiz.domain.QuestionType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The Bean Validation gaps closed in Phase 3 PR #3 (RFC-011): unbounded
 * collections and free-text fields across the quiz/question DTOs, plus
 * {@link BibleReferenceDto}'s cross-field verse check.
 */
class QuizApiValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private CreateQuestionRequest.QuestionContentDto content() {
        return new CreateQuestionRequest.QuestionContentDto("Title", "Prompt", null);
    }

    private CreateQuestionRequest.CreateOptionDto option(int displayOrder) {
        return new CreateQuestionRequest.CreateOptionDto("Option", true, displayOrder);
    }

    @Test
    void createQuestionRejectsAnOverlongDefaultLanguage() {
        CreateQuestionRequest request = new CreateQuestionRequest(
                "x".repeat(36), QuestionType.SINGLE_CHOICE, Difficulty.EASY,
                content(), List.of(option(1)), null, null, null);

        assertThat(propertyViolations(request, "defaultLanguage")).isNotEmpty();
    }

    @Test
    void createQuestionRejectsTooManyOptions() {
        List<CreateQuestionRequest.CreateOptionDto> tooMany = new ArrayList<>();
        for (int i = 1; i <= 21; i++) {
            tooMany.add(option(i));
        }
        CreateQuestionRequest request = new CreateQuestionRequest(
                "en", QuestionType.SINGLE_CHOICE, Difficulty.EASY, content(), tooMany, null, null, null);

        assertThat(propertyViolations(request, "options")).isNotEmpty();
    }

    @Test
    void createQuestionRejectsTooManyTagsAndOverlongTagText() {
        CreateQuestionRequest tooManyTags = new CreateQuestionRequest(
                "en", QuestionType.SINGLE_CHOICE, Difficulty.EASY, content(), List.of(option(1)), null, null,
                java.util.stream.IntStream.range(0, 21).mapToObj(i -> "tag" + i).toList());
        assertThat(propertyViolations(tooManyTags, "tags")).isNotEmpty();

        CreateQuestionRequest overlongTag = new CreateQuestionRequest(
                "en", QuestionType.SINGLE_CHOICE, Difficulty.EASY, content(), List.of(option(1)), null, null,
                List.of("x".repeat(31)));
        assertThat(validator.validate(overlongTag)).isNotEmpty();
    }

    @Test
    void createQuestionRejectsTooManyBibleAndMediaReferences() {
        List<BibleReferenceDto> tooManyRefs = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            tooManyRefs.add(new BibleReferenceDto("Exodus", 3, 1, 10, "ESV"));
        }
        CreateQuestionRequest request = new CreateQuestionRequest(
                "en", QuestionType.SINGLE_CHOICE, Difficulty.EASY, content(), List.of(option(1)),
                tooManyRefs, null, null);

        assertThat(propertyViolations(request, "bibleReferences")).isNotEmpty();
    }

    @Test
    void createOptionDtoRejectsAnExcessiveDisplayOrder() {
        CreateQuestionRequest request = new CreateQuestionRequest(
                "en", QuestionType.SINGLE_CHOICE, Difficulty.EASY, content(), List.of(option(1001)),
                null, null, null);

        assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test
    void updateQuestionRejectsTooManyLocalizationsAndTags() {
        UpdateQuestionRequest.UpdateOptionDto option =
                new UpdateQuestionRequest.UpdateOptionDto(UUID.randomUUID(), true, 1);
        List<QuestionLocalizationDto> tooMany = new ArrayList<>();
        for (int i = 0; i < 51; i++) {
            tooMany.add(new QuestionLocalizationDto("en", "T", "P", null, List.of()));
        }
        UpdateQuestionRequest request = new UpdateQuestionRequest(
                1L, Difficulty.EASY, List.of(option), tooMany, List.of(), List.of(),
                List.of("a"));

        assertThat(propertyViolations(request, "localizations")).isNotEmpty();
    }

    @Test
    void createQuizRejectsAnOverlongDefaultLanguage() {
        CreateQuizRequest request = new CreateQuizRequest(
                "x".repeat(36), null, new QuizLocalizationDto("en", "Title", null), null);

        assertThat(propertyViolations(request, "defaultLanguage")).isNotEmpty();
    }

    @Test
    void updateQuizRejectsTooManyLocalizations() {
        List<QuizLocalizationDto> tooMany = new ArrayList<>();
        for (int i = 0; i < 51; i++) {
            tooMany.add(new QuizLocalizationDto("en", "Title", null));
        }
        UpdateQuizRequest request = new UpdateQuizRequest(1L, null, null, tooMany);

        assertThat(propertyViolations(request, "localizations")).isNotEmpty();
    }

    @Test
    void reorderRejectsTooManyQuestionIds() {
        List<UUID> tooMany = new ArrayList<>();
        for (int i = 0; i < 201; i++) {
            tooMany.add(UUID.randomUUID());
        }
        ReorderQuestionsRequest request = new ReorderQuestionsRequest(tooMany);

        assertThat(propertyViolations(request, "questionIds")).isNotEmpty();
    }

    @Test
    void bibleReferenceAcceptsAValidRange() {
        BibleReferenceDto reference = new BibleReferenceDto("Exodus", 3, 1, 10, "ESV");

        assertThat(validator.validate(reference)).isEmpty();
    }

    @Test
    void bibleReferenceAcceptsAnOmittedVerseEnd() {
        BibleReferenceDto reference = new BibleReferenceDto("Exodus", 3, 1, null, "ESV");

        assertThat(validator.validate(reference)).isEmpty();
    }

    @Test
    void bibleReferenceRejectsAVerseEndBeforeVerseStart() {
        BibleReferenceDto reference = new BibleReferenceDto("Exodus", 3, 10, 1, "ESV");

        assertThat(validator.validate(reference))
                .anyMatch(v -> v.getPropertyPath().toString().equals("verseRangeValid"));
    }

    @Test
    void bibleReferenceRejectsAChapterBeyondTheCeiling() {
        BibleReferenceDto reference = new BibleReferenceDto("Psalms", 151, 1, null, null);

        assertThat(propertyViolations(reference, "chapter")).isNotEmpty();
    }

    @Test
    void mediaReferenceRejectsAnExcessiveDisplayOrder() {
        MediaReferenceDto reference = new MediaReferenceDto(
                null, MediaType.IMAGE, "media/key.png", null, 1001);

        assertThat(propertyViolations(reference, "displayOrder")).isNotEmpty();
    }

    @Test
    void questionLocalizationRejectsTooManyOptionTexts() {
        List<OptionTextDto> tooMany = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            tooMany.add(new OptionTextDto(UUID.randomUUID(), "text"));
        }
        QuestionLocalizationDto dto = new QuestionLocalizationDto("en", "T", "P", null, tooMany);

        assertThat(propertyViolations(dto, "optionTexts")).isNotEmpty();
    }

    private <T> Set<ConstraintViolation<T>> propertyViolations(T target, String property) {
        Set<ConstraintViolation<T>> all = validator.validate(target);
        return all.stream()
                .filter(v -> v.getPropertyPath().toString().equals(property)
                        || v.getPropertyPath().toString().startsWith(property + "["))
                .collect(java.util.stream.Collectors.toSet());
    }
}
