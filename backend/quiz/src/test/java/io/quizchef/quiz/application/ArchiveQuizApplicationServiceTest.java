package io.quizchef.quiz.application;

import static io.quizchef.quiz.application.QuizApplicationTestFixtures.englishQuizOwnedBy;
import static io.quizchef.quiz.application.QuizApplicationTestFixtures.quizMaster;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.quizchef.common.event.DomainEventPublisher;
import io.quizchef.identity.application.AuthorizationService;
import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.quiz.domain.Quiz;
import io.quizchef.quiz.domain.QuizState;
import io.quizchef.quiz.domain.event.QuizArchivedEvent;
import io.quizchef.quiz.domain.exception.QuizArchivedException;
import io.quizchef.quiz.domain.exception.QuizNotArchivableException;
import io.quizchef.quiz.infrastructure.persistence.QuizRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ArchiveQuizApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-15T10:00:00Z");

    private final QuizRepository quizRepository = mock(QuizRepository.class);
    private final AuthorizationService authorizationService = mock(AuthorizationService.class);
    private final DomainEventPublisher eventPublisher = mock(DomainEventPublisher.class);
    private final ArchiveQuizApplicationService service = new ArchiveQuizApplicationService(
            quizRepository, authorizationService, eventPublisher, Clock.fixed(NOW, ZoneOffset.UTC));

    private final CurrentUser owner = quizMaster(UUID.randomUUID());

    private Quiz storedQuiz(Quiz quiz) {
        when(quizRepository.findById(quiz.getId())).thenReturn(Optional.of(quiz));
        return quiz;
    }

    @Test
    void shouldArchivePublishedQuizAndPublishEvent() {
        Quiz quiz = storedQuiz(englishQuizOwnedBy(owner));
        quiz.addQuestion(UUID.randomUUID());
        quiz.publish();

        QuizView view = service.archive(owner, new ArchiveQuizCommand(quiz.getId()));

        assertThat(view.state()).isEqualTo(QuizState.ARCHIVED);
        ArgumentCaptor<QuizArchivedEvent> event = ArgumentCaptor.forClass(QuizArchivedEvent.class);
        verify(eventPublisher).publish(event.capture());
        assertThat(event.getValue().quizId()).isEqualTo(quiz.getId());
        assertThat(event.getValue().occurredAt()).isEqualTo(NOW);
    }

    @Test
    void shouldRejectDraft() {
        Quiz quiz = storedQuiz(englishQuizOwnedBy(owner));

        assertThatExceptionOfType(QuizNotArchivableException.class)
                .isThrownBy(() -> service.archive(owner, new ArchiveQuizCommand(quiz.getId())));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void archivingTwiceIsRejected() {
        Quiz quiz = storedQuiz(englishQuizOwnedBy(owner));
        quiz.addQuestion(UUID.randomUUID());
        quiz.publish();
        quiz.archive();

        assertThatExceptionOfType(QuizArchivedException.class)
                .isThrownBy(() -> service.archive(owner, new ArchiveQuizCommand(quiz.getId())));
        verifyNoInteractions(eventPublisher);
    }
}
