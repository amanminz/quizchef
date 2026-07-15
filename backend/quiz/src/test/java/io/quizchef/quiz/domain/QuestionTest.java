package io.quizchef.quiz.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import org.junit.jupiter.api.Test;

class QuestionTest {

    private static List<Option> twoOptionsOneCorrect() {
        return List.of(
                Option.of("Moses", true, 1),
                Option.of("Aaron", false, 2));
    }

    private static Question singleChoice(List<Option> options) {
        return Question.create(
                "Exodus leader", "Who led Israel out of Egypt?", "See Exodus 3.",
                QuestionType.SINGLE_CHOICE, Difficulty.EASY, options);
    }

    @Test
    void shouldCreateSingleChoiceQuestionWithExactlyOneCorrectOption() {
        Question question = singleChoice(twoOptionsOneCorrect());

        assertThat(question.getId()).isNotNull();
        assertThat(question.getQuestionType()).isEqualTo(QuestionType.SINGLE_CHOICE);
        assertThat(question.options()).hasSize(2);
        assertThat(question.options().getFirst().text()).isEqualTo("Moses");
    }

    @Test
    void singleChoiceRejectsZeroOrManyCorrectOptions() {
        assertThatIllegalArgumentException().isThrownBy(() -> singleChoice(List.of(
                Option.of("Moses", false, 1),
                Option.of("Aaron", false, 2))));

        assertThatIllegalArgumentException().isThrownBy(() -> singleChoice(List.of(
                Option.of("Moses", true, 1),
                Option.of("Aaron", true, 2))));
    }

    @Test
    void multipleChoiceRequiresAtLeastOneCorrectOption() {
        assertThatIllegalArgumentException().isThrownBy(() -> Question.create(
                "Apostles", "Which of these were apostles?", null,
                QuestionType.MULTIPLE_CHOICE, Difficulty.MEDIUM,
                List.of(Option.of("Judas Iscariot", false, 1), Option.of("Herod", false, 2))));

        Question question = Question.create(
                "Apostles", "Which of these were apostles?", null,
                QuestionType.MULTIPLE_CHOICE, Difficulty.MEDIUM,
                List.of(Option.of("Peter", true, 1), Option.of("John", true, 2),
                        Option.of("Herod", false, 3)));
        assertThat(question.options()).hasSize(3);
    }

    @Test
    void trueFalseRequiresExactlyTwoOptionsWithOneCorrect() {
        assertThatIllegalArgumentException().isThrownBy(() -> Question.create(
                "Jonah", "Jonah was swallowed by a great fish.", null,
                QuestionType.TRUE_FALSE, Difficulty.EASY,
                List.of(Option.of("True", true, 1))));

        assertThatIllegalArgumentException().isThrownBy(() -> Question.create(
                "Jonah", "Jonah was swallowed by a great fish.", null,
                QuestionType.TRUE_FALSE, Difficulty.EASY,
                List.of(Option.of("True", true, 1), Option.of("False", true, 2))));

        Question question = Question.create(
                "Jonah", "Jonah was swallowed by a great fish.", null,
                QuestionType.TRUE_FALSE, Difficulty.EASY,
                List.of(Option.of("True", true, 1), Option.of("False", false, 2)));
        assertThat(question.options()).hasSize(2);
    }

    @Test
    void shouldRejectQuestionWithoutOptions() {
        assertThatIllegalArgumentException().isThrownBy(() -> singleChoice(List.of()));
    }

    @Test
    void shouldRejectDuplicateOptionOrder() {
        assertThatIllegalArgumentException().isThrownBy(() -> singleChoice(List.of(
                Option.of("Moses", true, 1),
                Option.of("Aaron", false, 1))));
    }

    @Test
    void shouldRejectBlankOptionText() {
        assertThatIllegalArgumentException().isThrownBy(() -> Option.of("  ", true, 1));
    }

    @Test
    void replaceOptionsRevalidatesTypeRules() {
        Question question = singleChoice(twoOptionsOneCorrect());

        assertThatIllegalArgumentException().isThrownBy(() -> question.replaceOptions(List.of(
                Option.of("Moses", true, 1),
                Option.of("Aaron", true, 2))));
        assertThat(question.options()).hasSize(2);

        question.replaceOptions(List.of(
                Option.of("Moses", true, 1),
                Option.of("Aaron", false, 2),
                Option.of("Miriam", false, 3)));
        assertThat(question.options()).hasSize(3);
    }

    @Test
    void shouldRejectDuplicateMediaReferenceOrder() {
        Question question = singleChoice(twoOptionsOneCorrect());

        assertThatIllegalArgumentException().isThrownBy(() -> question.updateMediaReferences(List.of(
                MediaReference.of(MediaType.IMAGE, "media/one.png", "Red Sea", 1),
                MediaReference.of(MediaType.IMAGE, "media/two.png", "Desert", 1))));
    }

    @Test
    void shouldAttachBibleAndMediaReferences() {
        Question question = singleChoice(twoOptionsOneCorrect());

        question.updateBibleReferences(List.of(BibleReference.range("Exodus", 3, 1, 10)));
        question.updateMediaReferences(List.of(
                MediaReference.of(MediaType.IMAGE, "media/burning-bush.png", "Burning bush", 1)));

        assertThat(question.bibleReferences()).hasSize(1);
        assertThat(question.mediaReferences()).hasSize(1);
    }
}
