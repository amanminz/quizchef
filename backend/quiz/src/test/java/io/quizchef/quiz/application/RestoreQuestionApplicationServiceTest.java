package io.quizchef.quiz.application;

import static io.quizchef.quiz.application.QuestionApplicationTestFixtures.englishQuestionOwnedBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.quizchef.identity.application.AuthorizationService;
import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.identity.domain.IdentityType;
import io.quizchef.identity.domain.Role;
import io.quizchef.quiz.domain.Question;
import io.quizchef.quiz.domain.QuestionState;
import io.quizchef.quiz.domain.exception.QuestionNotFoundException;
import io.quizchef.quiz.domain.exception.QuestionNotRestorableException;
import io.quizchef.quiz.infrastructure.persistence.QuestionRepository;
import io.quizchef.quiz.infrastructure.persistence.TagRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RestoreQuestionApplicationServiceTest {

    private final QuestionRepository questionRepository = mock(QuestionRepository.class);
    private final TagRepository tagRepository = mock(TagRepository.class);
    private final AuthorizationService authorizationService = mock(AuthorizationService.class);
    private final RestoreQuestionApplicationService service = new RestoreQuestionApplicationService(
            questionRepository, tagRepository, authorizationService);

    private final CurrentUser owner = quizMaster();
    private final CurrentUser stranger = quizMaster();

    private static CurrentUser quizMaster() {
        return CurrentUser.authenticated(UUID.randomUUID(), IdentityType.REGISTERED,
                Set.of(Role.USER, Role.QUIZ_MASTER));
    }

    private Question storedQuestion(Question question) {
        when(questionRepository.findById(question.getId())).thenReturn(Optional.of(question));
        when(tagRepository.findAllById(any())).thenReturn(List.of());
        return question;
    }

    @Test
    void restoresAnArchivedQuestionToPublishedWithTheSameId() {
        Question question = storedQuestion(englishQuestionOwnedBy(owner));
        question.publish();
        question.archive();
        UUID originalId = question.getId();

        QuestionView view = service.restore(owner, question.getId());

        assertThat(view.state()).isEqualTo(QuestionState.PUBLISHED);
        assertThat(view.id()).isEqualTo(originalId);
    }

    @Test
    void aRepeatedRestoreIsRejected() {
        Question question = storedQuestion(englishQuestionOwnedBy(owner));
        question.publish();
        question.archive();
        service.restore(owner, question.getId());

        assertThatExceptionOfType(QuestionNotRestorableException.class)
                .isThrownBy(() -> service.restore(owner, question.getId()));
    }

    @Test
    void onlyArchivedQuestionsAreRestorable() {
        Question draft = storedQuestion(englishQuestionOwnedBy(owner));

        assertThatExceptionOfType(QuestionNotRestorableException.class)
                .isThrownBy(() -> service.restore(owner, draft.getId()));
    }

    @Test
    void anotherOwnersQuestionReadsAsNotFound() {
        Question question = storedQuestion(englishQuestionOwnedBy(owner));
        question.publish();
        question.archive();

        assertThatExceptionOfType(QuestionNotFoundException.class)
                .isThrownBy(() -> service.restore(stranger, question.getId()));
    }
}
