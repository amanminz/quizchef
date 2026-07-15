package io.quizchef.quiz.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.quizchef.identity.domain.IdentityReference;
import io.quizchef.identity.domain.IdentityType;
import io.quizchef.quiz.domain.exception.DefaultLocalizationRequiredException;
import io.quizchef.quiz.domain.exception.DuplicateQuizQuestionException;
import io.quizchef.quiz.domain.exception.QuizArchivedException;
import io.quizchef.quiz.domain.exception.QuizNotPublishableException;
import io.quizchef.quiz.domain.exception.QuizQuestionsLockedException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QuizTest {

    private static final IdentityReference OWNER =
            new IdentityReference(UUID.randomUUID(), IdentityType.REGISTERED);
    private static final LanguageCode EN = LanguageCode.of("en");
    private static final LanguageCode KN = LanguageCode.of("kn");

    private Quiz quiz() {
        return Quiz.create(new QuizLocalization(EN, "BELC Bible Quiz", "Weekly quiz"), OWNER);
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
    void shouldCreateWithDefaultLanguageLocalizationFromDayOne() {
        Quiz quiz = quiz();

        assertThat(quiz.getDefaultLanguage()).isEqualTo(EN);
        assertThat(quiz.defaultLocalization().title()).isEqualTo("BELC Bible Quiz");
        assertThat(quiz.localizations()).hasSize(1);
    }

    @Test
    void defaultLanguageIsConfigurablePerQuiz() {
        Quiz quiz = Quiz.create(
                new QuizLocalization(KN, "ಬೈಬಲ್ ರಸಪ್ರಶ್ನೆ", null), OWNER);

        assertThat(quiz.getDefaultLanguage()).isEqualTo(KN);
        assertThat(quiz.defaultLocalization().title()).isEqualTo("ಬೈಬಲ್ ರಸಪ್ರಶ್ನೆ");
    }

    @Test
    void shouldRejectBlankTitle() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Quiz.create(new QuizLocalization(EN, "   ", null), OWNER));
    }

    @Test
    void localizeAddsTranslationsAndKeepsOnePerLanguage() {
        Quiz quiz = quiz();

        quiz.localize(new QuizLocalization(KN, "ಬೈಬಲ್ ರಸಪ್ರಶ್ನೆ", "ವಾರದ ರಸಪ್ರಶ್ನೆ"));
        quiz.localize(new QuizLocalization(KN, "ಬಿಇಎಲ್ಸಿ ರಸಪ್ರಶ್ನೆ", null));

        assertThat(quiz.localizations()).hasSize(2);
        assertThat(quiz.localization(KN).orElseThrow().title()).isEqualTo("ಬಿಇಎಲ್ಸಿ ರಸಪ್ರಶ್ನೆ");
        assertThat(quiz.localization(EN).orElseThrow().title()).isEqualTo("BELC Bible Quiz");
    }

    @Test
    void defaultLanguageLocalizationCannotBeRemoved() {
        Quiz quiz = quiz();
        quiz.localize(new QuizLocalization(KN, "ಬೈಬಲ್ ರಸಪ್ರಶ್ನೆ", null));

        assertThatExceptionOfType(DefaultLocalizationRequiredException.class)
                .isThrownBy(() -> quiz.removeLocalization(EN));

        quiz.removeLocalization(KN);
        assertThat(quiz.localizations()).hasSize(1);
        assertThatIllegalArgumentException().isThrownBy(() -> quiz.removeLocalization(KN));
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
        assertThatExceptionOfType(QuizArchivedException.class)
                .isThrownBy(() -> quiz.localize(new QuizLocalization(KN, "ಹೊಸ", null)));
        assertThatExceptionOfType(QuizArchivedException.class)
                .isThrownBy(() -> quiz.removeLocalization(KN));
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

        quiz.localize(new QuizLocalization(EN, "Renamed", "New description"));
        quiz.changeVisibility(QuizVisibility.PUBLIC);
        quiz.updateSettings(new QuizSettings(true, true, 60, false, false));

        assertThat(quiz.defaultLocalization().title()).isEqualTo("Renamed");
        assertThat(quiz.defaultLocalization().description()).isEqualTo("New description");
        assertThat(quiz.getVisibility()).isEqualTo(QuizVisibility.PUBLIC);
        assertThat(quiz.getSettings().questionTimeLimitSeconds()).isEqualTo(60);
    }
}
