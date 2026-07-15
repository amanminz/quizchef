package io.quizchef.quiz.application;

import io.quizchef.quiz.domain.BibleReference;
import io.quizchef.quiz.domain.Difficulty;
import io.quizchef.quiz.domain.MediaReference;
import io.quizchef.quiz.domain.QuestionType;
import java.util.List;

/**
 * Creates a draft question. The owner is never part of the command — it is
 * always the authenticated caller. Option texts are in the default
 * language; ids are assigned by the server.
 *
 * @param bibleReferences optional; empty when null
 * @param mediaReferences optional; empty when null
 * @param tags            optional tag names; resolved or created by name
 */
public record CreateQuestionCommand(
        String defaultLanguage,
        QuestionType questionType,
        Difficulty difficulty,
        String title,
        String prompt,
        String explanation,
        List<CreateQuestionOptionCommand> options,
        List<BibleReference> bibleReferences,
        List<MediaReference> mediaReferences,
        List<String> tags
) {

    public record CreateQuestionOptionCommand(String text, boolean correct, int displayOrder) {
    }
}
