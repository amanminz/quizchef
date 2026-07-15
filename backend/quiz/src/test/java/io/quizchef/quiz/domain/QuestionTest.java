package io.quizchef.quiz.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.quizchef.quiz.domain.exception.DefaultLocalizationRequiredException;
import java.util.List;
import org.junit.jupiter.api.Test;

class QuestionTest {

    private static final LanguageCode EN = LanguageCode.of("en");
    private static final LanguageCode KN = LanguageCode.of("kn");

    private static final Option MOSES = Option.of(true, 1);
    private static final Option AARON = Option.of(false, 2);

    private static QuestionLocalization englishContent() {
        return new QuestionLocalization(
                EN, "Exodus leader", "Who led Israel out of Egypt?", "See Exodus 3.");
    }

    private static Question singleChoice() {
        return Question.create(englishContent(),
                QuestionType.SINGLE_CHOICE, Difficulty.EASY,
                List.of(MOSES, AARON),
                List.of(MOSES.localized(EN, "Moses"), AARON.localized(EN, "Aaron")));
    }

    private static Question singleChoice(List<Option> options, List<OptionLocalization> texts) {
        return Question.create(englishContent(),
                QuestionType.SINGLE_CHOICE, Difficulty.EASY, options, texts);
    }

    @Test
    void shouldCreateSingleChoiceQuestionWithExactlyOneCorrectOption() {
        Question question = singleChoice();

        assertThat(question.getId()).isNotNull();
        assertThat(question.getQuestionType()).isEqualTo(QuestionType.SINGLE_CHOICE);
        assertThat(question.getDefaultLanguage()).isEqualTo(EN);
        assertThat(question.defaultLocalization().title()).isEqualTo("Exodus leader");
        assertThat(question.options()).hasSize(2);
        assertThat(question.optionLocalizations(EN))
                .extracting(OptionLocalization::text)
                .containsExactly("Moses", "Aaron");
    }

    @Test
    void singleChoiceRejectsZeroOrManyCorrectOptions() {
        Option first = Option.of(false, 1);
        Option second = Option.of(false, 2);
        assertThatIllegalArgumentException().isThrownBy(() -> singleChoice(
                List.of(first, second),
                List.of(first.localized(EN, "Moses"), second.localized(EN, "Aaron"))));

        Option third = Option.of(true, 1);
        Option fourth = Option.of(true, 2);
        assertThatIllegalArgumentException().isThrownBy(() -> singleChoice(
                List.of(third, fourth),
                List.of(third.localized(EN, "Moses"), fourth.localized(EN, "Aaron"))));
    }

    @Test
    void multipleChoiceRequiresAtLeastOneCorrectOption() {
        Option judas = Option.of(false, 1);
        Option herod = Option.of(false, 2);
        assertThatIllegalArgumentException().isThrownBy(() -> Question.create(
                new QuestionLocalization(EN, "Apostles", "Which of these were apostles?", null),
                QuestionType.MULTIPLE_CHOICE, Difficulty.MEDIUM,
                List.of(judas, herod),
                List.of(judas.localized(EN, "Judas Iscariot"), herod.localized(EN, "Herod"))));

        Option peter = Option.of(true, 1);
        Option john = Option.of(true, 2);
        Option herodAgain = Option.of(false, 3);
        Question question = Question.create(
                new QuestionLocalization(EN, "Apostles", "Which of these were apostles?", null),
                QuestionType.MULTIPLE_CHOICE, Difficulty.MEDIUM,
                List.of(peter, john, herodAgain),
                List.of(peter.localized(EN, "Peter"), john.localized(EN, "John"),
                        herodAgain.localized(EN, "Herod")));
        assertThat(question.options()).hasSize(3);
    }

    @Test
    void trueFalseRequiresExactlyTwoOptionsWithOneCorrect() {
        Option lonelyTrue = Option.of(true, 1);
        assertThatIllegalArgumentException().isThrownBy(() -> Question.create(
                new QuestionLocalization(EN, "Jonah", "Jonah was swallowed by a great fish.", null),
                QuestionType.TRUE_FALSE, Difficulty.EASY,
                List.of(lonelyTrue),
                List.of(lonelyTrue.localized(EN, "True"))));

        Option bothTrue = Option.of(true, 1);
        Option alsoTrue = Option.of(true, 2);
        assertThatIllegalArgumentException().isThrownBy(() -> Question.create(
                new QuestionLocalization(EN, "Jonah", "Jonah was swallowed by a great fish.", null),
                QuestionType.TRUE_FALSE, Difficulty.EASY,
                List.of(bothTrue, alsoTrue),
                List.of(bothTrue.localized(EN, "True"), alsoTrue.localized(EN, "False"))));

        Option trueOption = Option.of(true, 1);
        Option falseOption = Option.of(false, 2);
        Question question = Question.create(
                new QuestionLocalization(EN, "Jonah", "Jonah was swallowed by a great fish.", null),
                QuestionType.TRUE_FALSE, Difficulty.EASY,
                List.of(trueOption, falseOption),
                List.of(trueOption.localized(EN, "True"), falseOption.localized(EN, "False")));
        assertThat(question.options()).hasSize(2);
    }

    @Test
    void shouldRejectQuestionWithoutOptions() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> singleChoice(List.of(), List.of()));
    }

    @Test
    void shouldRejectDuplicateOptionOrder() {
        Option first = Option.of(true, 1);
        Option second = Option.of(false, 1);
        assertThatIllegalArgumentException().isThrownBy(() -> singleChoice(
                List.of(first, second),
                List.of(first.localized(EN, "Moses"), second.localized(EN, "Aaron"))));
    }

    @Test
    void shouldRejectBlankOptionText() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> MOSES.localized(EN, "  "));
    }

    @Test
    void optionTextsMustCoverExactlyTheOptions() {
        // missing one option's text
        assertThatIllegalArgumentException().isThrownBy(() -> singleChoice(
                List.of(MOSES, AARON),
                List.of(MOSES.localized(EN, "Moses"))));

        // text for an option the question does not have
        assertThatIllegalArgumentException().isThrownBy(() -> singleChoice(
                List.of(MOSES, AARON),
                List.of(MOSES.localized(EN, "Moses"), AARON.localized(EN, "Aaron"),
                        Option.of(false, 3).localized(EN, "Miriam"))));

        // the same option localized twice
        assertThatIllegalArgumentException().isThrownBy(() -> singleChoice(
                List.of(MOSES, AARON),
                List.of(MOSES.localized(EN, "Moses"), MOSES.localized(EN, "Moshe"))));

        // text in a different language than the localization
        assertThatIllegalArgumentException().isThrownBy(() -> singleChoice(
                List.of(MOSES, AARON),
                List.of(MOSES.localized(EN, "Moses"), AARON.localized(KN, "ಆರೋನ್"))));
    }

    @Test
    void localizeAddsCompleteTranslationAndKeepsOnePerLanguage() {
        Question question = singleChoice();

        question.localize(
                new QuestionLocalization(KN, "ವಿಮೋಚನೆಯ ನಾಯಕ", "ಇಸ್ರಾಯೇಲನ್ನು ಈಜಿಪ್ಟಿನಿಂದ ಹೊರತಂದವರು ಯಾರು?", null),
                List.of(MOSES.localized(KN, "ಮೋಶೆ"), AARON.localized(KN, "ಆರೋನ್")));
        question.localize(
                new QuestionLocalization(KN, "ನಾಯಕ", "ಇಸ್ರಾಯೇಲನ್ನು ಹೊರತಂದವರು ಯಾರು?", null),
                List.of(MOSES.localized(KN, "ಮೋಶೆ"), AARON.localized(KN, "ಆರೋನ್")));

        assertThat(question.localizations()).hasSize(2);
        assertThat(question.localization(KN).orElseThrow().title()).isEqualTo("ನಾಯಕ");
        assertThat(question.optionLocalizations(KN))
                .extracting(OptionLocalization::text)
                .containsExactly("ಮೋಶೆ", "ಆರೋನ್");
        assertThat(question.optionLocalizations(EN))
                .extracting(OptionLocalization::text)
                .containsExactly("Moses", "Aaron");
    }

    @Test
    void localizeRejectsIncompleteOptionCoverage() {
        Question question = singleChoice();

        assertThatIllegalArgumentException().isThrownBy(() -> question.localize(
                new QuestionLocalization(KN, "ನಾಯಕ", "ಯಾರು?", null),
                List.of(MOSES.localized(KN, "ಮೋಶೆ"))));
        assertThat(question.localizations()).hasSize(1);
    }

    @Test
    void defaultLanguageLocalizationCannotBeRemoved() {
        Question question = singleChoice();
        question.localize(
                new QuestionLocalization(KN, "ನಾಯಕ", "ಯಾರು?", null),
                List.of(MOSES.localized(KN, "ಮೋಶೆ"), AARON.localized(KN, "ಆರೋನ್")));

        assertThatExceptionOfType(DefaultLocalizationRequiredException.class)
                .isThrownBy(() -> question.removeLocalization(EN));

        question.removeLocalization(KN);
        assertThat(question.localizations()).hasSize(1);
        assertThat(question.optionLocalizations(KN)).isEmpty();
        assertThatIllegalArgumentException().isThrownBy(() -> question.removeLocalization(KN));
    }

    @Test
    void replaceOptionsRevalidatesTypeRules() {
        Question question = singleChoice();

        Option bothCorrectA = Option.of(true, 1);
        Option bothCorrectB = Option.of(true, 2);
        assertThatIllegalArgumentException().isThrownBy(() -> question.replaceOptions(
                List.of(bothCorrectA, bothCorrectB),
                List.of(bothCorrectA.localized(EN, "Moses"), bothCorrectB.localized(EN, "Aaron"))));
        assertThat(question.options()).hasSize(2);

        Option moses = Option.of(true, 1);
        Option aaron = Option.of(false, 2);
        Option miriam = Option.of(false, 3);
        question.replaceOptions(
                List.of(moses, aaron, miriam),
                List.of(moses.localized(EN, "Moses"), aaron.localized(EN, "Aaron"),
                        miriam.localized(EN, "Miriam")));
        assertThat(question.options()).hasSize(3);
        assertThat(question.optionLocalizations(EN)).hasSize(3);
    }

    @Test
    void replaceOptionsKeepsTranslationsThatStillCoverEveryOption() {
        Question question = singleChoice();
        question.localize(
                new QuestionLocalization(KN, "ನಾಯಕ", "ಯಾರು?", null),
                List.of(MOSES.localized(KN, "ಮೋಶೆ"), AARON.localized(KN, "ಆರೋನ್")));

        // dropping an option leaves the translation complete for what remains
        question.replaceOptions(
                List.of(MOSES),
                List.of(MOSES.localized(EN, "Moses")));

        assertThat(question.localization(KN)).isPresent();
        assertThat(question.optionLocalizations(KN))
                .extracting(OptionLocalization::text)
                .containsExactly("ಮೋಶೆ");
    }

    @Test
    void replaceOptionsDropsTranslationsLeftIncomplete() {
        Question question = singleChoice();
        question.localize(
                new QuestionLocalization(KN, "ನಾಯಕ", "ಯಾರು?", null),
                List.of(MOSES.localized(KN, "ಮೋಶೆ"), AARON.localized(KN, "ಆರೋನ್")));

        // a new option has no Kannada text, so the whole language goes
        Option miriam = Option.of(false, 3);
        question.replaceOptions(
                List.of(MOSES, AARON, miriam),
                List.of(MOSES.localized(EN, "Moses"), AARON.localized(EN, "Aaron"),
                        miriam.localized(EN, "Miriam")));

        assertThat(question.localization(KN)).isEmpty();
        assertThat(question.optionLocalizations(KN)).isEmpty();
        assertThat(question.optionLocalizations(EN)).hasSize(3);
    }

    @Test
    void shouldRejectDuplicateMediaReferenceOrder() {
        Question question = singleChoice();

        assertThatIllegalArgumentException().isThrownBy(() -> question.updateMediaReferences(List.of(
                MediaReference.of(MediaType.IMAGE, "media/one.png", "Red Sea", 1),
                MediaReference.of(MediaType.IMAGE, "media/two.png", "Desert", 1))));
    }

    @Test
    void shouldAttachBibleAndMediaReferences() {
        Question question = singleChoice();

        question.updateBibleReferences(List.of(BibleReference.range("Exodus", 3, 1, 10)));
        question.updateMediaReferences(List.of(
                MediaReference.of(MediaType.IMAGE, "media/burning-bush.png", "Burning bush", 1)));

        assertThat(question.bibleReferences()).hasSize(1);
        assertThat(question.mediaReferences()).hasSize(1);
    }
}
