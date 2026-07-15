package io.quizchef.quiz.application;

import static io.quizchef.quiz.application.QuestionApplicationTestFixtures.englishQuestionOwnedBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.quizchef.identity.application.AuthorizationService;
import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.identity.domain.IdentityType;
import io.quizchef.identity.domain.Permission;
import io.quizchef.identity.domain.Role;
import io.quizchef.quiz.domain.Question;
import io.quizchef.quiz.domain.Tag;
import io.quizchef.quiz.domain.exception.QuestionNotFoundException;
import io.quizchef.quiz.infrastructure.persistence.QuestionRepository;
import io.quizchef.quiz.infrastructure.persistence.TagRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QuestionQueryServiceTest {

    private final QuestionRepository questionRepository = mock(QuestionRepository.class);
    private final TagRepository tagRepository = mock(TagRepository.class);
    private final AuthorizationService authorizationService = mock(AuthorizationService.class);
    private final QuestionQueryService service =
            new QuestionQueryService(questionRepository, tagRepository, authorizationService);

    private final CurrentUser owner = quizMaster();
    private final CurrentUser stranger = quizMaster();

    private static CurrentUser quizMaster() {
        return CurrentUser.authenticated(UUID.randomUUID(), IdentityType.REGISTERED,
                Set.of(Role.USER, Role.QUIZ_MASTER));
    }

    @Test
    void ownerSeesTheirQuestionWithTagsAndLocalizations() {
        Question question = englishQuestionOwnedBy(owner);
        Tag exodus = Tag.named("exodus");
        question.updateTags(Set.of(exodus.getId()));
        when(questionRepository.findById(question.getId())).thenReturn(Optional.of(question));
        when(tagRepository.findAllById(any())).thenReturn(List.of(exodus));

        QuestionView view = service.question(owner, question.getId());

        assertThat(view.id()).isEqualTo(question.getId());
        assertThat(view.localizations()).hasSize(1);
        assertThat(view.tags()).extracting(QuestionView.TagView::name).containsExactly("exodus");
        verify(authorizationService).authorize(owner, Permission.QUIZ_VIEW);
    }

    @Test
    void questionsOfOtherOwnersAreIndistinguishableFromMissing() {
        Question question = englishQuestionOwnedBy(owner);
        when(questionRepository.findById(question.getId())).thenReturn(Optional.of(question));

        assertThatExceptionOfType(QuestionNotFoundException.class)
                .isThrownBy(() -> service.question(stranger, question.getId()));
    }

    @Test
    void unknownQuestionsAreNotFound() {
        UUID unknown = UUID.randomUUID();
        when(questionRepository.findById(unknown)).thenReturn(Optional.empty());

        assertThatExceptionOfType(QuestionNotFoundException.class)
                .isThrownBy(() -> service.question(owner, unknown));
    }
}
