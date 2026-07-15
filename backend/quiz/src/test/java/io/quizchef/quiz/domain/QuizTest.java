package io.quizchef.quiz.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.quizchef.identity.domain.IdentityReference;
import io.quizchef.identity.domain.IdentityType;
import io.quizchef.quiz.domain.exception.DuplicateQuizQuestionException;
import io.quizchef.quiz.domain.exception.QuizArchivedException;
import io.quizchef.quiz.domain.exception.QuizNotPublishableException;
import io.quizchef.quiz.domain.exception.QuizQuestionsLockedException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QuizTest {

    private static final IdentityReference OWNER =
            new IdentityReference(UUID.randomUUID(), IdentityType.REGISTERED);

    private Quiz quiz() {
        return Quiz.create("BELC Bible Quiz", "Weekly quiz", OWNER);
    }

    @Test
    void shouldCreateDraftPrivateQuizWithDefaultSettings() {
        Quiz quiz = quiz();

        assertThat(quiz.getId()).isNotNull();
        assertThat(quiz.getState()).isEqualTo(QuizState.DRAFT);
        assertThat(quiz.getVisibility()).isEqualTo(QuizVisibility.PRIVATE);
        assertThat(quiz.getSettings()).isEqualTo(QuizSettings.defaults());
        assertThat(quiz.getOwnerIdentity()).isEqualTo(OWNER);
        assertThat(quiz.questions()).isEmpty();
    }

    @Test
    void shouldRejectBlankTitle() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Quiz.create("   ", null, OWNER));
    }

    @Test
    void shouldAppendQuestionsInSequentialOrder() {
        Quiz quiz = quiz();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        quiz.addQuestion(first);
        quiz.addQuestion(second);

        assertThat(quiz.questions())
                .extracting(QuizQuestion::questionId, QuizQuestion::displayOrder)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(first, 1),
                        org.assertj.core.groups.Tuple.tuple(second, 2));
    }

    @Test
    void shouldRejectDuplicateQuestion() {
        Quiz quiz = quiz();
        UUID questionId = UUID.randomUUID();
        quiz.addQuestion(questionId);

        assertThatExceptionOfType(DuplicateQuizQuestionException.class)
                .isThrownBy(() -> quiz.addQuestion(questionId));
    }

    @Test
    void shouldKeepOrderUniqueAfterRemovalAndReAddition() {
        Quiz quiz = quiz();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        quiz.addQuestion(first);
        quiz.addQuestion(second);

        quiz.removeQuestion(first);
        UUID third = UUID.randomUUID();
        quiz.addQuestion(third);

        assertThat(quiz.questions())
                .extracting(QuizQuestion::displayOrder)
                .doesNotHaveDuplicates();
        assertThat(quiz.questions())
                .extracting(QuizQuestion::questionId)
                .containsExactly(second, third);
    }

    @Test
    void shouldNotPublishWithoutQuestions() {
        assertThatExceptionOfType(QuizNotPublishableException.class)
                .isThrownBy(() -> quiz().publish());
    }

    @Test
    void shouldPublishDraftWithQuestionsExactlyOnce() {
        Quiz quiz = quiz();
        quiz.addQuestion(UUID.randomUUID());

        quiz.publish();

        assertThat(quiz.isPublished()).isTrue();
        assertThatExceptionOfType(QuizNotPublishableException.class)
                .isThrownBy(quiz::publish);
    }

    @Test
    void publishedQuizMayGainButNeverLoseQuestions() {
        Quiz quiz = quiz();
        UUID original = UUID.randomUUID();
        quiz.addQuestion(original);
        quiz.publish();

        quiz.addQuestion(UUID.randomUUID());

        assertThatExceptionOfType(QuizQuestionsLockedException.class)
                .isThrownBy(() -> quiz.removeQuestion(original));
        assertThat(quiz.questions()).hasSize(2);
    }

    @Test
    void archivedQuizIsReadOnly() {
        Quiz quiz = quiz();
        quiz.addQuestion(UUID.randomUUID());
        quiz.archive();

        assertThat(quiz.isArchived()).isTrue();
        assertThatExceptionOfType(QuizArchivedException.class).isThrownBy(() -> quiz.rename("New"));
        assertThatExceptionOfType(QuizArchivedException.class)
                .isThrownBy(() -> quiz.addQuestion(UUID.randomUUID()));
        assertThatExceptionOfType(QuizArchivedException.class).isThrownBy(quiz::publish);
        assertThatExceptionOfType(QuizArchivedException.class)
                .isThrownBy(() -> quiz.changeVisibility(QuizVisibility.PUBLIC));
        assertThatExceptionOfType(QuizArchivedException.class).isThrownBy(quiz::archive);
    }

    @Test
    void shouldUpdateMetadataWhileModifiable() {
        Quiz quiz = quiz();

        quiz.rename("Renamed");
        quiz.describe("New description");
        quiz.changeVisibility(QuizVisibility.PUBLIC);
        quiz.updateSettings(new QuizSettings(true, true, 60, false, false));

        assertThat(quiz.getTitle()).isEqualTo("Renamed");
        assertThat(quiz.getDescription()).isEqualTo("New description");
        assertThat(quiz.getVisibility()).isEqualTo(QuizVisibility.PUBLIC);
        assertThat(quiz.getSettings().questionTimeLimitSeconds()).isEqualTo(60);
    }
}
