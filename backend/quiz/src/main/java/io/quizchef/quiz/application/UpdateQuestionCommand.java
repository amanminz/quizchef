package io.quizchef.quiz.application;

import io.quizchef.quiz.domain.BibleReference;
import io.quizchef.quiz.domain.Difficulty;
import io.quizchef.quiz.domain.MediaReference;
import java.util.List;
import java.util.UUID;

/**
 * Replaces a draft question's full editable representation. The
 * localizations are the complete new set and must include the default
 * language; options keep their ids to preserve translations (new options
 * carry fresh, client-generated ids). The version must match the
 * question's current version.
 */
public record UpdateQuestionCommand(
        UUID questionId,
        long version,
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
