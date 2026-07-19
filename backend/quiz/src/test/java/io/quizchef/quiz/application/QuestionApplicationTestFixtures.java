package io.quizchef.quiz.application;

import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.quiz.domain.Difficulty;
import io.quizchef.quiz.domain.LanguageCode;
import io.quizchef.quiz.domain.Option;
import io.quizchef.quiz.domain.OptionLocalization;
import io.quizchef.quiz.domain.Question;
import io.quizchef.quiz.domain.QuestionLocalization;
import io.quizchef.quiz.domain.QuestionType;
import java.util.List;

/**
 * Shared builders for question application service tests.
 */
final class QuestionApplicationTestFixtures {

    static final LanguageCode EN = LanguageCode.of("en");
    static final LanguageCode KN = LanguageCode.of("kn");

    private QuestionApplicationTestFixtures() {
    }

    static CreateQuestionCommand createCommand(List<String> tags) {
        return new CreateQuestionCommand(
                "en", QuestionType.SINGLE_CHOICE, Difficulty.EASY,
                "Exodus leader", "Who led Israel out of Egypt?", "See Exodus 3.",
                List.of(new CreateQuestionCommand.CreateQuestionOptionCommand("Moses", true, 1),
                        new CreateQuestionCommand.CreateQuestionOptionCommand("Aaron", false, 2)),
                null, null, tags);
    }

    /**
     * A draft single-choice question owned by the given caller, in English.
     */
    static Question englishQuestionOwnedBy(CurrentUser owner) {
        Option moses = Option.of(true, 1);
        Option aaron = Option.of(false, 2);
        return Question.create(
                new QuestionLocalization(EN, "Exodus leader", "Who led Israel out of Egypt?", "See Exodus 3."),
                owner.reference(), QuestionType.SINGLE_CHOICE, Difficulty.EASY,
                List.of(moses, aaron),
                List.of(moses.localized(EN, "Moses"), aaron.localized(EN, "Aaron")));
    }

    /**
     * The full editable representation of the question, unchanged — the
     * starting point every update test mutates one field of.
     */
    static UpdateQuestionCommand unchangedUpdateOf(Question question) {
        return new UpdateQuestionCommand(
                question.getId(),
                question.getVersion(),
                null,
                null,
                question.getDifficulty(),
                question.options().stream()
                        .map(option -> new UpdateQuestionCommand.UpdateQuestionOptionCommand(
                                option.id(), option.correct(), option.displayOrder()))
                        .toList(),
                question.localizations().stream()
                        .map(localization -> contentOf(question, localization))
                        .toList(),
                question.bibleReferences(),
                question.mediaReferences(),
                List.of());
    }

    private static QuestionContentCommand contentOf(Question question, QuestionLocalization localization) {
        List<OptionTextCommand> texts = question.optionLocalizations(localization.languageCode()).stream()
                .map(text -> new OptionTextCommand(text.optionId(), text.text()))
                .toList();
        return new QuestionContentCommand(
                localization.languageCode().value(),
                localization.title(),
                localization.prompt(),
                localization.explanation(),
                texts);
    }

    static QuestionContentCommand kannadaContentFor(Question question) {
        List<OptionTextCommand> texts = question.options().stream()
                .map(option -> new OptionTextCommand(option.id(), "ಆಯ್ಕೆ " + option.displayOrder()))
                .toList();
        return new QuestionContentCommand("kn", "ನಾಯಕ", "ಯಾರು?", null, texts);
    }

    static List<OptionLocalization> optionTextsIn(LanguageCode language, List<Option> options) {
        return options.stream()
                .map(option -> option.localized(language, "text " + option.displayOrder()))
                .toList();
    }
}
