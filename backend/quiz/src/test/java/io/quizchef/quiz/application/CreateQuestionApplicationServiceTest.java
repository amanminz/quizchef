package io.quizchef.quiz.application;

import static io.quizchef.quiz.application.QuestionApplicationTestFixtures.createCommand;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.quizchef.common.exception.ForbiddenException;
import io.quizchef.identity.application.AuthorizationService;
import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.identity.domain.IdentityType;
import io.quizchef.identity.domain.Permission;
import io.quizchef.identity.domain.Role;
import io.quizchef.quiz.domain.Difficulty;
import io.quizchef.quiz.domain.OptionLocalization;
import io.quizchef.quiz.domain.QuestionSource;
import io.quizchef.quiz.domain.QuestionState;
import io.quizchef.quiz.domain.QuestionType;
import io.quizchef.quiz.domain.Tag;
import io.quizchef.quiz.infrastructure.persistence.QuestionRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CreateQuestionApplicationServiceTest {

    private final QuestionRepository questionRepository = mock(QuestionRepository.class);
    private final AuthorizationService authorizationService = mock(AuthorizationService.class);
    private final TagResolver tagResolver = mock(TagResolver.class);
    private final CreateQuestionApplicationService service =
            new CreateQuestionApplicationService(questionRepository, authorizationService, tagResolver);

    private final CurrentUser caller = CurrentUser.authenticated(
            UUID.randomUUID(), IdentityType.REGISTERED, Set.of(Role.USER, Role.QUIZ_MASTER));

    @BeforeEach
    void resolveTagsToNothingByDefault() {
        when(tagResolver.resolve(anyList())).thenReturn(List.of());
    }

    @Test
    void shouldCreateDraftManualQuestionOwnedByCaller() {
        QuestionView view = service.create(caller, createCommand(List.of()));

        assertThat(view.state()).isEqualTo(QuestionState.DRAFT);
        assertThat(view.source()).isEqualTo(QuestionSource.MANUAL);
        assertThat(view.ownerIdentityId()).isEqualTo(caller.identityId());
        assertThat(view.questionType()).isEqualTo(QuestionType.SINGLE_CHOICE);
        assertThat(view.difficulty()).isEqualTo(Difficulty.EASY);
        assertThat(view.defaultLanguage().value()).isEqualTo("en");
        assertThat(view.options()).hasSize(2);
        verify(authorizationService).authorize(caller, Permission.QUIZ_CREATE);
        verify(questionRepository).save(any());
    }

    @Test
    void shouldAssignOptionIdsAndLocalizeThemInTheDefaultLanguage() {
        QuestionView view = service.create(caller, createCommand(List.of()));

        assertThat(view.options()).allSatisfy(option -> assertThat(option.id()).isNotNull());
        assertThat(view.localizations()).singleElement().satisfies(localization -> {
            assertThat(localization.languageCode().value()).isEqualTo("en");
            assertThat(localization.optionTexts())
                    .extracting(OptionLocalization::text)
                    .containsExactly("Moses", "Aaron");
            assertThat(localization.optionTexts())
                    .extracting(OptionLocalization::optionId)
                    .containsExactlyElementsOf(view.options().stream()
                            .map(io.quizchef.quiz.domain.Option::id).toList());
        });
    }

    @Test
    void shouldStoreResolvedTagIds() {
        Tag exodus = Tag.named("exodus");
        Tag moses = Tag.named("moses");
        when(tagResolver.resolve(List.of("Exodus", "moses"))).thenReturn(List.of(exodus, moses));

        QuestionView view = service.create(caller, createCommand(List.of("Exodus", "moses")));

        assertThat(view.tags())
                .extracting(QuestionView.TagView::name)
                .containsExactly("exodus", "moses");
    }

    @Test
    void shouldRejectStructurallyInvalidQuestion() {
        CreateQuestionCommand twoCorrectSingleChoice = new CreateQuestionCommand(
                "en", QuestionType.SINGLE_CHOICE, Difficulty.EASY,
                "Exodus leader", "Who led Israel out of Egypt?", null,
                List.of(new CreateQuestionCommand.CreateQuestionOptionCommand("Moses", true, 1),
                        new CreateQuestionCommand.CreateQuestionOptionCommand("Aaron", true, 2)),
                null, null, List.of());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.create(caller, twoCorrectSingleChoice));
        verifyNoInteractions(questionRepository);
    }

    @Test
    void deniedAuthorizationCreatesNothing() {
        doThrow(new ForbiddenException()).when(authorizationService)
                .authorize(caller, Permission.QUIZ_CREATE);

        assertThatExceptionOfType(ForbiddenException.class)
                .isThrownBy(() -> service.create(caller, createCommand(List.of())));
        verifyNoInteractions(questionRepository);
    }
}
