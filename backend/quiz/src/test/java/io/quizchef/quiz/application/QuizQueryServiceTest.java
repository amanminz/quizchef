package io.quizchef.quiz.application;

import static io.quizchef.quiz.application.QuizApplicationTestFixtures.englishQuizOwnedBy;
import static io.quizchef.quiz.application.QuizApplicationTestFixtures.quizMaster;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.quizchef.identity.application.AuthorizationService;
import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.quiz.domain.Quiz;
import io.quizchef.quiz.domain.QuizVisibility;
import io.quizchef.quiz.domain.exception.QuizNotFoundException;
import io.quizchef.quiz.infrastructure.persistence.QuizRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QuizQueryServiceTest {

    private final QuizRepository quizRepository = mock(QuizRepository.class);
    private final AuthorizationService authorizationService = mock(AuthorizationService.class);
    private final QuizQueryService service = new QuizQueryService(quizRepository, authorizationService);

    private final CurrentUser owner = quizMaster(UUID.randomUUID());
    private final CurrentUser stranger = quizMaster(UUID.randomUUID());

    @Test
    void ownerSeesTheirPrivateQuiz() {
        Quiz quiz = englishQuizOwnedBy(owner);
        when(quizRepository.findById(quiz.getId())).thenReturn(Optional.of(quiz));

        QuizView view = service.quiz(owner, quiz.getId());

        assertThat(view.id()).isEqualTo(quiz.getId());
        assertThat(view.localizations()).hasSize(1);
    }

    @Test
    void privateQuizzesOfOthersAreIndistinguishableFromMissing() {
        Quiz quiz = englishQuizOwnedBy(owner);
        when(quizRepository.findById(quiz.getId())).thenReturn(Optional.of(quiz));

        assertThatExceptionOfType(QuizNotFoundException.class)
                .isThrownBy(() -> service.quiz(stranger, quiz.getId()));
    }

    @Test
    void nonPrivateQuizzesAreVisibleToAnyViewer() {
        Quiz quiz = englishQuizOwnedBy(owner);
        quiz.changeVisibility(QuizVisibility.UNLISTED);
        when(quizRepository.findById(quiz.getId())).thenReturn(Optional.of(quiz));

        assertThat(service.quiz(stranger, quiz.getId()).id()).isEqualTo(quiz.getId());
    }
}
