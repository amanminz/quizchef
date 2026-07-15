package io.quizchef.quiz.application;

import static io.quizchef.quiz.application.QuizApplicationTestFixtures.EN;
import static io.quizchef.quiz.application.QuizApplicationTestFixtures.KN;
import static io.quizchef.quiz.application.QuizApplicationTestFixtures.englishQuizOwnedBy;
import static io.quizchef.quiz.application.QuizApplicationTestFixtures.quizMaster;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.quizchef.common.exception.ForbiddenException;
import io.quizchef.identity.application.AuthorizationService;
import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.quiz.domain.Quiz;
import io.quizchef.quiz.domain.QuizVisibility;
import io.quizchef.quiz.domain.exception.QuizArchivedException;
import io.quizchef.quiz.domain.exception.QuizContentLockedException;
import io.quizchef.quiz.domain.exception.QuizModifiedConcurrentlyException;
import io.quizchef.quiz.domain.exception.QuizNotFoundException;
import io.quizchef.quiz.infrastructure.persistence.QuizRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UpdateQuizApplicationServiceTest {

    private final QuizRepository quizRepository = mock(QuizRepository.class);
    private final AuthorizationService authorizationService = mock(AuthorizationService.class);
    private final UpdateQuizApplicationService service =
            new UpdateQuizApplicationService(quizRepository, authorizationService);

    private final CurrentUser owner = quizMaster(UUID.randomUUID());
    private final CurrentUser stranger = quizMaster(UUID.randomUUID());

    private Quiz storedQuiz(Quiz quiz) {
        when(quizRepository.findById(any())).thenReturn(Optional.of(quiz));
        return quiz;
    }

    @Test
    void shouldUpdateDraftSettingsLocalizationsAndVisibility() {
        Quiz quiz = storedQuiz(englishQuizOwnedBy(owner));

        QuizView view = service.update(owner, new UpdateQuizCommand(
                quiz.getId(), quiz.getVersion(),
                QuizVisibility.PUBLIC,
                new QuizSettingsCommand(true, true, 60, false, false),
                List.of(new QuizLocalizationCommand("en", "Renamed", null),
                        new QuizLocalizationCommand("kn", "ಬೈಬಲ್ ರಸಪ್ರಶ್ನೆ", null))));

        assertThat(view.visibility()).isEqualTo(QuizVisibility.PUBLIC);
        assertThat(view.settings().questionTimeLimitSeconds()).isEqualTo(60);
        assertThat(quiz.localization(EN).orElseThrow().title()).isEqualTo("Renamed");
        assertThat(quiz.localization(KN)).isPresent();
    }

    @Test
    void providedLocalizationsReplaceTheFullSet() {
        Quiz quiz = storedQuiz(englishQuizOwnedBy(owner));
        quiz.localize(new io.quizchef.quiz.domain.QuizLocalization(KN, "ಬೈಬಲ್ ರಸಪ್ರಶ್ನೆ", null));

        service.update(owner, new UpdateQuizCommand(quiz.getId(), quiz.getVersion(), null, null,
                List.of(new QuizLocalizationCommand("en", "Only English now", null))));

        assertThat(quiz.localization(KN)).isEmpty();
        assertThat(quiz.localizations()).hasSize(1);
    }

    @Test
    void shouldRejectStaleVersion() {
        Quiz quiz = storedQuiz(englishQuizOwnedBy(owner));

        assertThatExceptionOfType(QuizModifiedConcurrentlyException.class)
                .isThrownBy(() -> service.update(owner, new UpdateQuizCommand(
                        quiz.getId(), quiz.getVersion() + 1, QuizVisibility.PUBLIC, null, null)));
    }

    @Test
    void publishedQuizAcceptsOnlyVisibility() {
        Quiz quiz = storedQuiz(englishQuizOwnedBy(owner));
        quiz.addQuestion(UUID.randomUUID());
        quiz.publish();

        assertThatExceptionOfType(QuizContentLockedException.class)
                .isThrownBy(() -> service.update(owner, new UpdateQuizCommand(
                        quiz.getId(), quiz.getVersion(), null,
                        new QuizSettingsCommand(true, true, 60, false, false), null)));

        QuizView view = service.update(owner, new UpdateQuizCommand(
                quiz.getId(), quiz.getVersion(), QuizVisibility.PUBLIC, null, null));
        assertThat(view.visibility()).isEqualTo(QuizVisibility.PUBLIC);
    }

    @Test
    void shouldRejectArchivedQuizUpdate() {
        Quiz quiz = storedQuiz(englishQuizOwnedBy(owner));
        quiz.addQuestion(UUID.randomUUID());
        quiz.publish();
        quiz.archive();

        assertThatExceptionOfType(QuizArchivedException.class)
                .isThrownBy(() -> service.update(owner, new UpdateQuizCommand(
                        quiz.getId(), quiz.getVersion(), QuizVisibility.PUBLIC, null, null)));
    }

    @Test
    void nonOwnersCannotUpdate() {
        Quiz privateQuiz = storedQuiz(englishQuizOwnedBy(owner));
        assertThatExceptionOfType(QuizNotFoundException.class)
                .as("private quizzes do not reveal their existence")
                .isThrownBy(() -> service.update(stranger, new UpdateQuizCommand(
                        privateQuiz.getId(), 0, QuizVisibility.PUBLIC, null, null)));

        Quiz publicQuiz = storedQuiz(englishQuizOwnedBy(owner));
        publicQuiz.changeVisibility(QuizVisibility.PUBLIC);
        assertThatExceptionOfType(ForbiddenException.class)
                .isThrownBy(() -> service.update(stranger, new UpdateQuizCommand(
                        publicQuiz.getId(), 0, QuizVisibility.PRIVATE, null, null)));
    }
}
