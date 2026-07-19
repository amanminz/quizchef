package io.quizchef.quiz.application;

import static io.quizchef.quiz.application.QuestionApplicationTestFixtures.englishQuestionOwnedBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.quizchef.identity.application.AuthorizationService;
import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.identity.domain.IdentityType;
import io.quizchef.identity.domain.Role;
import io.quizchef.quiz.domain.Question;
import io.quizchef.quiz.domain.exception.QuestionInUseException;
import io.quizchef.quiz.domain.exception.QuestionNotFoundException;
import io.quizchef.quiz.infrastructure.persistence.QuestionRepository;
import io.quizchef.quiz.infrastructure.persistence.QuizRepository;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeleteQuestionApplicationServiceTest {

    private final QuestionRepository questionRepository = mock(QuestionRepository.class);
    private final QuizRepository quizRepository = mock(QuizRepository.class);
    private final AuthorizationService authorizationService = mock(AuthorizationService.class);
    private final DeleteQuestionApplicationService service = new DeleteQuestionApplicationService(
            questionRepository, quizRepository, authorizationService);

    private final CurrentUser owner = quizMaster();
    private final CurrentUser stranger = quizMaster();

    private static CurrentUser quizMaster() {
        return CurrentUser.authenticated(UUID.randomUUID(), IdentityType.REGISTERED,
                Set.of(Role.USER, Role.QUIZ_MASTER));
    }

    private Question storedQuestion(Question question) {
        when(questionRepository.findById(question.getId())).thenReturn(Optional.of(question));
        return question;
    }

    @Test
    void deletesAnUnusedQuestion() {
        Question question = storedQuestion(englishQuestionOwnedBy(owner));
        when(quizRepository.countByQuestionId(question.getId())).thenReturn(0L);

        service.delete(owner, question.getId());

        verify(questionRepository).delete(question);
    }

    @Test
    void anUnusedQuestionIsDeletableInEveryLifecycleState() {
        // One consistent rule: unused means deletable — draft, published,
        // or archived alike.
        Question published = storedQuestion(englishQuestionOwnedBy(owner));
        published.publish();
        when(quizRepository.countByQuestionId(published.getId())).thenReturn(0L);
        service.delete(owner, published.getId());
        verify(questionRepository).delete(published);

        Question archived = storedQuestion(englishQuestionOwnedBy(owner));
        archived.publish();
        archived.archive();
        when(quizRepository.countByQuestionId(archived.getId())).thenReturn(0L);
        service.delete(owner, archived.getId());
        verify(questionRepository).delete(archived);
    }

    @Test
    void aReferencedQuestionIsRejectedWithItsReferenceCount() {
        Question question = storedQuestion(englishQuestionOwnedBy(owner));
        when(quizRepository.countByQuestionId(question.getId())).thenReturn(2L);

        assertThatExceptionOfType(QuestionInUseException.class)
                .isThrownBy(() -> service.delete(owner, question.getId()))
                .satisfies(exception -> {
                    assertThat(exception.quizCount()).isEqualTo(2L);
                    assertThat(exception.getMessage()).contains("2 quizzes");
                });
        verify(questionRepository, never()).delete(question);
    }

    @Test
    void anotherOwnersQuestionReadsAsNotFound() {
        Question question = storedQuestion(englishQuestionOwnedBy(owner));

        assertThatExceptionOfType(QuestionNotFoundException.class)
                .isThrownBy(() -> service.delete(stranger, question.getId()));
        verify(questionRepository, never()).delete(question);
    }

    @Test
    void usageCountsTheReferencingQuizzes() {
        Question question = storedQuestion(englishQuestionOwnedBy(owner));
        when(quizRepository.countByQuestionId(question.getId())).thenReturn(3L);

        assertThat(service.quizReferenceCount(owner, question.getId())).isEqualTo(3L);
    }
}
