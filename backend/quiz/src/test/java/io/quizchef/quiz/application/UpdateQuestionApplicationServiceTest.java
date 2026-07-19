package io.quizchef.quiz.application;

import static io.quizchef.quiz.application.QuestionApplicationTestFixtures.KN;
import static io.quizchef.quiz.application.QuestionApplicationTestFixtures.englishQuestionOwnedBy;
import static io.quizchef.quiz.application.QuestionApplicationTestFixtures.kannadaContentFor;
import static io.quizchef.quiz.application.QuestionApplicationTestFixtures.unchangedUpdateOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.quizchef.identity.application.AuthorizationService;
import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.identity.domain.IdentityType;
import io.quizchef.identity.domain.Role;
import io.quizchef.quiz.domain.Difficulty;
import io.quizchef.quiz.domain.LanguageCode;
import io.quizchef.quiz.domain.QuestionType;
import io.quizchef.quiz.domain.Question;
import io.quizchef.quiz.domain.Tag;
import io.quizchef.quiz.domain.exception.QuestionArchivedException;
import io.quizchef.quiz.domain.exception.QuestionContentLockedException;
import io.quizchef.quiz.domain.exception.QuestionModifiedConcurrentlyException;
import io.quizchef.quiz.domain.exception.QuestionNotFoundException;
import io.quizchef.quiz.infrastructure.persistence.QuestionRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UpdateQuestionApplicationServiceTest {

    private final QuestionRepository questionRepository = mock(QuestionRepository.class);
    private final AuthorizationService authorizationService = mock(AuthorizationService.class);
    private final TagResolver tagResolver = mock(TagResolver.class);
    private final UpdateQuestionApplicationService service =
            new UpdateQuestionApplicationService(questionRepository, authorizationService, tagResolver);

    private final CurrentUser owner = quizMaster();
    private final CurrentUser stranger = quizMaster();

    private static CurrentUser quizMaster() {
        return CurrentUser.authenticated(UUID.randomUUID(), IdentityType.REGISTERED,
                Set.of(Role.USER, Role.QUIZ_MASTER));
    }

    @BeforeEach
    void resolveTagsToNothingByDefault() {
        when(tagResolver.resolve(anyList())).thenReturn(List.of());
    }

    private Question storedQuestion(Question question) {
        when(questionRepository.findById(question.getId())).thenReturn(Optional.of(question));
        return question;
    }

    @Test
    void shouldUpdateDraftDifficultyAndTags() {
        Question question = storedQuestion(englishQuestionOwnedBy(owner));
        Tag exodus = Tag.named("exodus");
        when(tagResolver.resolve(List.of("Exodus"))).thenReturn(List.of(exodus));

        UpdateQuestionCommand command = unchangedUpdateOf(question);
        QuestionView view = service.update(owner, new UpdateQuestionCommand(
                command.questionId(), command.version(), null, null, Difficulty.HARD, command.options(),
                command.localizations(), command.bibleReferences(), command.mediaReferences(),
                List.of("Exodus")));

        assertThat(view.difficulty()).isEqualTo(Difficulty.HARD);
        assertThat(view.tags()).extracting(QuestionView.TagView::name).containsExactly("exodus");
        assertThat(question.tagIds()).containsExactly(exodus.getId());
    }

    @Test
    void localizationsReplaceTheFullSet() {
        Question question = storedQuestion(englishQuestionOwnedBy(owner));

        // add Kannada
        UpdateQuestionCommand command = unchangedUpdateOf(question);
        List<QuestionContentCommand> withKannada = new ArrayList<>(command.localizations());
        withKannada.add(kannadaContentFor(question));
        service.update(owner, new UpdateQuestionCommand(
                command.questionId(), command.version(), null, null, command.difficulty(), command.options(),
                withKannada, command.bibleReferences(), command.mediaReferences(), List.of()));
        assertThat(question.localization(KN)).isPresent();

        // omitting it removes it again
        UpdateQuestionCommand englishOnly = unchangedUpdateOf(question);
        service.update(owner, new UpdateQuestionCommand(
                englishOnly.questionId(), englishOnly.version(), null, null, englishOnly.difficulty(),
                englishOnly.options(),
                englishOnly.localizations().stream()
                        .filter(content -> content.languageCode().equals("en"))
                        .toList(),
                englishOnly.bibleReferences(), englishOnly.mediaReferences(), List.of()));

        assertThat(question.localization(KN)).isEmpty();
        assertThat(question.localizations()).hasSize(1);
    }

    @Test
    void shouldRejectLocalizationsMissingTheDefaultLanguage() {
        Question question = storedQuestion(englishQuestionOwnedBy(owner));
        UpdateQuestionCommand command = unchangedUpdateOf(question);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.update(owner, new UpdateQuestionCommand(
                        command.questionId(), command.version(), null, null, command.difficulty(),
                        command.options(), List.of(kannadaContentFor(question)),
                        command.bibleReferences(), command.mediaReferences(), List.of())))
                .withMessageContaining("default language");
    }

    @Test
    void aDraftChangesItsQuestionTypeThroughThePut() {
        Question question = storedQuestion(englishQuestionOwnedBy(owner));
        UpdateQuestionCommand command = unchangedUpdateOf(question);

        QuestionView view = service.update(owner, new UpdateQuestionCommand(
                command.questionId(), command.version(), QuestionType.MULTIPLE_CHOICE, null,
                command.difficulty(), command.options(), command.localizations(),
                command.bibleReferences(), command.mediaReferences(), List.of()));

        assertThat(view.questionType()).isEqualTo(QuestionType.MULTIPLE_CHOICE);
    }

    @Test
    void aDraftMovesItsDefaultLanguageToAFullyLocalizedOne() {
        Question question = storedQuestion(englishQuestionOwnedBy(owner));
        UpdateQuestionCommand command = unchangedUpdateOf(question);
        List<QuestionContentCommand> withKannada = new ArrayList<>(command.localizations());
        withKannada.add(kannadaContentFor(question));

        service.update(owner, new UpdateQuestionCommand(
                command.questionId(), command.version(), null, "kn",
                command.difficulty(), command.options(), withKannada,
                command.bibleReferences(), command.mediaReferences(), List.of()));

        assertThat(question.getDefaultLanguage()).isEqualTo(LanguageCode.of("kn"));
        assertThat(question.localization(LanguageCode.of("en"))).isPresent();
    }

    @Test
    void theNewDefaultLanguageMustBeInTheLocalizations() {
        Question question = storedQuestion(englishQuestionOwnedBy(owner));
        UpdateQuestionCommand command = unchangedUpdateOf(question);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.update(owner, new UpdateQuestionCommand(
                        command.questionId(), command.version(), null, "kn",
                        command.difficulty(), command.options(), command.localizations(),
                        command.bibleReferences(), command.mediaReferences(), List.of())))
                .withMessageContaining("default language");
    }

    @Test
    void shouldRejectStaleVersion() {
        Question question = storedQuestion(englishQuestionOwnedBy(owner));
        UpdateQuestionCommand command = unchangedUpdateOf(question);

        assertThatExceptionOfType(QuestionModifiedConcurrentlyException.class)
                .isThrownBy(() -> service.update(owner, new UpdateQuestionCommand(
                        command.questionId(), command.version() + 1, null, null, command.difficulty(),
                        command.options(), command.localizations(), command.bibleReferences(),
                        command.mediaReferences(), command.tags())));
    }

    @Test
    void publishedAndArchivedQuestionsAreImmutable() {
        Question published = storedQuestion(englishQuestionOwnedBy(owner));
        published.publish();

        assertThatExceptionOfType(QuestionContentLockedException.class)
                .isThrownBy(() -> service.update(owner, unchangedUpdateOf(published)));

        published.archive();
        assertThatExceptionOfType(QuestionArchivedException.class)
                .isThrownBy(() -> service.update(owner, unchangedUpdateOf(published)));
    }

    @Test
    void questionsOfOtherOwnersAreIndistinguishableFromMissing() {
        Question question = storedQuestion(englishQuestionOwnedBy(owner));

        assertThatExceptionOfType(QuestionNotFoundException.class)
                .isThrownBy(() -> service.update(stranger, unchangedUpdateOf(question)));
    }
}
