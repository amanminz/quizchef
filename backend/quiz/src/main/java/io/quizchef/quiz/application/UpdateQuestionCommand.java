package io.quizchef.quiz.application;

import io.quizchef.quiz.domain.BibleReference;
import io.quizchef.quiz.domain.Difficulty;
import io.quizchef.quiz.domain.MediaReference;
import io.quizchef.quiz.domain.QuestionType;
import java.util.List;
import java.util.UUID;

/**
 * Replaces a draft question's full editable representation. The
 * localizations are the complete new set and must include the default
 * language; options keep their ids to preserve translations (new options
 * carry fresh, client-generated ids). The version must match the
 * question's current version.
 *
 * <p>{@code questionType} and {@code defaultLanguage} are the draft's new
 * values; {@code null} keeps the question's current one (older clients
 * simply never send them).
 */
public record UpdateQuestionCommand(
        UUID questionId,
        long version,
        QuestionType questionType,
        String defaultLanguage,
        Difficulty difficulty,
        List<UpdateQuestionOptionCommand> options,
        List<QuestionContentCommand> localizations,
        List<BibleReference> bibleReferences,
        List<MediaReference> mediaReferences,
        List<String> tags
) {

    public record UpdateQuestionOptionCommand(UUID id, boolean correct, int displayOrder) {
    }
}
