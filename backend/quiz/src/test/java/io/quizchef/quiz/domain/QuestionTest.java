package io.quizchef.quiz.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.quizchef.identity.domain.IdentityReference;
import io.quizchef.identity.domain.IdentityType;
import io.quizchef.quiz.domain.exception.DefaultLocalizationRequiredException;
import io.quizchef.quiz.domain.exception.QuestionArchivedException;
import io.quizchef.quiz.domain.exception.QuestionContentLockedException;
import io.quizchef.quiz.domain.exception.QuestionNotArchivableException;
import io.quizchef.quiz.domain.exception.QuestionNotPublishableException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QuestionTest {

    private static final IdentityReference OWNER =
            new IdentityReference(UUID.randomUUID(), IdentityType.REGISTERED);
    private static final LanguageCode EN = LanguageCode.of("en");
    private static final LanguageCode KN = LanguageCode.of("kn");

    private static final Option MOSES = Option.of(true, 1);
    private static final Option AARON = Option.of(false, 2);

    private static QuestionLocalization englishContent() {
        return new QuestionLocalization(
                EN, "Exodus leader", "Who led Israel out of Egypt?", "See Exodus 3.");
    }

    private static Question singleChoice() {
        return Question.create(englishContent(), OWNER,
                QuestionType.SINGLE_CHOICE, Difficulty.EASY,
                List.of(MOSES, AARON),
                List.of(MOSES.localized(EN, "Moses"), AARON.localized(EN, "Aaron")));
    }

    private static Question singleChoice(List<Option> options, List<OptionLocalization> texts) {
        return Question.create(englishContent(), OWNER,
                QuestionType.SINGLE_CHOICE, Difficulty.EASY, options, texts);
    }

    private static void localizeInKannada(Question question) {
        question.localize(
                new QuestionLocalization(KN, "ನಾಯಕ", "ಯಾರು?", null),
                List.of(MOSES.localized(KN, "ಮೋಶೆ"), AARON.localized(KN, "ಆರೋನ್")));
    }

    @Test
    void shouldCreateDraftManualQuestionOwnedByAuthor() {
        Question question = singleChoice();

        assertThat(question.getId()).isNotNull();
        assertThat(question.getState()).isEqualTo(QuestionState.DRAFT);
        assertThat(question.getSource()).isEqualTo(QuestionSource.MANUAL);
        assertThat(question.getOwnerIdentity()).isEqualTo(OWNER);
        assertThat(question.getQuestionType()).isEqualTo(QuestionType.SINGLE_CHOICE);
        assertThat(question.getDefaultLanguage()).isEqualTo(EN);
        assertThat(question.defaultLocalization().title()).isEqualTo("Exodus leader");
        assertThat(question.options()).hasSize(2);
        assertThat(question.optionLocalizations(EN))
                .extracting(OptionLocalization::text)
                .containsExactly("Moses", "Aaron");
        assertThat(question.tagIds()).isEmpty();
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
                OWNER, QuestionType.MULTIPLE_CHOICE, Difficulty.MEDIUM,
                List.of(judas, herod),
                List.of(judas.localized(EN, "Judas Iscariot"), herod.localized(EN, "Herod"))));

        Option peter = Option.of(true, 1);
        Option john = Option.of(true, 2);
        Option herodAgain = Option.of(false, 3);
        Question question = Question.create(
                new QuestionLocalization(EN, "Apostles", "Which of these were apostles?", null),
                OWNER, QuestionType.MULTIPLE_CHOICE, Difficulty.MEDIUM,
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
                OWNER, QuestionType.TRUE_FALSE, Difficulty.EASY,
                List.of(lonelyTrue),
                List.of(lonelyTrue.localized(EN, "True"))));

        Option bothTrue = Option.of(true, 1);
        Option alsoTrue = Option.of(true, 2);
        assertThatIllegalArgumentException().isThrownBy(() -> Question.create(
                new QuestionLocalization(EN, "Jonah", "Jonah was swallowed by a great fish.", null),
                OWNER, QuestionType.TRUE_FALSE, Difficulty.EASY,
                List.of(bothTrue, alsoTrue),
                List.of(bothTrue.localized(EN, "True"), alsoTrue.localized(EN, "False"))));

        Option trueOption = Option.of(true, 1);
        Option falseOption = Option.of(false, 2);
        Question question = Question.create(
                new QuestionLocalization(EN, "Jonah", "Jonah was swallowed by a great fish.", null),
                OWNER, QuestionType.TRUE_FALSE, Difficulty.EASY,
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
    void shouldRejectDuplicateOptionOrderOrId() {
        Option first = Option.of(true, 1);
        Option second = Option.of(false, 1);
        assertThatIllegalArgumentException().isThrownBy(() -> singleChoice(
                List.of(first, second),
                List.of(first.localized(EN, "Moses"), second.localized(EN, "Aaron"))));

        Option original = Option.of(true, 1);
        Option sameId = new Option(original.id(), false, 2);
        assertThatIllegalArgumentException().isThrownBy(() -> singleChoice(
                List.of(original, sameId),
                List.of(original.localized(EN, "Moses"), sameId.localized(EN, "Aaron"))));
    }

    @Test
    void shouldRejectBlankOptionText() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> MOSES.localized(EN, "  "));
    }

    @Test
    void optionTextsMustCoverExactlyTheOptions() {
        assertThatIllegalArgumentException().isThrownBy(() -> singleChoice(
                List.of(MOSES, AARON),
                List.of(MOSES.localized(EN, "Moses"))));

        assertThatIllegalArgumentException().isThrownBy(() -> singleChoice(
                List.of(MOSES, AARON),
                List.of(MOSES.localized(EN, "Moses"), AARON.localized(EN, "Aaron"),
                        Option.of(false, 3).localized(EN, "Miriam"))));

        assertThatIllegalArgumentException().isThrownBy(() -> singleChoice(
                List.of(MOSES, AARON),
                List.of(MOSES.localized(EN, "Moses"), MOSES.localized(EN, "Moshe"))));

        assertThatIllegalArgumentException().isThrownBy(() -> singleChoice(
                List.of(MOSES, AARON),
                List.of(MOSES.localized(EN, "Moses"), AARON.localized(KN, "ಆರೋನ್"))));
    }

    @Test
    void localizeAddsCompleteTranslationAndKeepsOnePerLanguage() {
        Question question = singleChoice();

        localizeInKannada(question);
        question.localize(
                new QuestionLocalization(KN, "ನಾಯಕ", "ಇಸ್ರಾಯೇಲನ್ನು ಹೊರತಂದವರು ಯಾರು?", null),
                List.of(MOSES.localized(KN, "ಮೋಶೆ"), AARON.localized(KN, "ಆರೋನ್")));

        assertThat(question.localizations()).hasSize(2);
        assertThat(question.localization(KN).orElseThrow().prompt())
                .isEqualTo("ಇಸ್ರಾಯೇಲನ್ನು ಹೊರತಂದವರು ಯಾರು?");
        assertThat(question.optionLocalizations(KN))
                .extracting(OptionLocalization::text)
                .containsExactly("ಮೋಶೆ", "ಆರೋನ್");
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
        localizeInKannada(question);

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
        localizeInKannada(question);

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
        localizeInKannada(question);

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
    void publishFreezesTheQuestion() {
        Question question = singleChoice();

        question.publish();

        assertThat(question.isPublished()).isTrue();
        assertThatExceptionOfType(QuestionNotPublishableException.class)
                .isThrownBy(question::publish);
        assertThatExceptionOfType(QuestionContentLockedException.class)
                .isThrownBy(() -> localizeInKannada(question));
        assertThatExceptionOfType(QuestionContentLockedException.class)
                .isThrownBy(() -> question.changeDifficulty(Difficulty.HARD));
        assertThatExceptionOfType(QuestionContentLockedException.class)
                .isThrownBy(() -> question.updateTags(Set.of(UUID.randomUUID())));
        assertThatExceptionOfType(QuestionContentLockedException.class)
                .isThrownBy(() -> question.replaceOptions(
                        List.of(MOSES), List.of(MOSES.localized(EN, "Moses"))));
        assertThatExceptionOfType(QuestionContentLockedException.class)
                .isThrownBy(() -> question.updateBibleReferences(List.of()));
    }

    @Test
    void draftCannotBeArchivedAndArchivingIsTerminal() {
        Question question = singleChoice();

        assertThatExceptionOfType(QuestionNotArchivableException.class)
                .isThrownBy(question::archive);

        question.publish();
        question.archive();

        assertThat(question.isArchived()).isTrue();
        assertThatExceptionOfType(QuestionArchivedException.class).isThrownBy(question::archive);
        assertThatExceptionOfType(QuestionArchivedException.class).isThrownBy(question::publish);
        assertThatExceptionOfType(QuestionArchivedException.class)
                .isThrownBy(() -> question.changeDifficulty(Difficulty.HARD));
        assertThatExceptionOfType(QuestionArchivedException.class)
                .isThrownBy(() -> localizeInKannada(question));
    }

    @Test
    void tagsAreReplacedAsAWholeWhileDraft() {
        Question question = singleChoice();
        UUID exodus = UUID.randomUUID();
        UUID moses = UUID.randomUUID();

        question.updateTags(Set.of(exodus, moses));
        assertThat(question.tagIds()).containsExactlyInAnyOrder(exodus, moses);

        question.updateTags(Set.of(exodus));
        assertThat(question.tagIds()).containsExactly(exodus);
    }

    @Test
    void difficultyIsEditableWhileDraft() {
        Question question = singleChoice();

        question.changeDifficulty(Difficulty.HARD);

        assertThat(question.getDifficulty()).isEqualTo(Difficulty.HARD);
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
