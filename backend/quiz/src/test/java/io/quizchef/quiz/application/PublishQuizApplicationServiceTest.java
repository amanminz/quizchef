package io.quizchef.quiz.application;

import static io.quizchef.quiz.application.QuizApplicationTestFixtures.EN;
import static io.quizchef.quiz.application.QuizApplicationTestFixtures.KN;
import static io.quizchef.quiz.application.QuizApplicationTestFixtures.englishQuizOwnedBy;
import static io.quizchef.quiz.application.QuizApplicationTestFixtures.questionLocalizedIn;
import static io.quizchef.quiz.application.QuizApplicationTestFixtures.quizMaster;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.quizchef.common.event.DomainEventPublisher;
import io.quizchef.identity.application.AuthorizationService;
import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.quiz.domain.Question;
import io.quizchef.quiz.domain.Quiz;
import io.quizchef.quiz.domain.QuizState;
import io.quizchef.quiz.domain.event.QuizPublishedEvent;
import io.quizchef.quiz.domain.exception.QuizNotPublishableException;
import io.quizchef.quiz.infrastructure.persistence.QuestionRepository;
import io.quizchef.quiz.infrastructure.persistence.QuizRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PublishQuizApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-15T10:00:00Z");

    private final QuizRepository quizRepository = mock(QuizRepository.class);
    private final QuestionRepository questionRepository = mock(QuestionRepository.class);
    private final AuthorizationService authorizationService = mock(AuthorizationService.class);
    private final DomainEventPublisher eventPublisher = mock(DomainEventPublisher.class);
    private final PublishQuizApplicationService service = new PublishQuizApplicationService(
            quizRepository, questionRepository, authorizationService, eventPublisher,
            Clock.fixed(NOW, ZoneOffset.UTC));

    private final CurrentUser owner = quizMaster(UUID.randomUUID());

    @Test
    void shouldPublishWhenEveryQuestionSpeaksTheDefaultLanguage() {
        Quiz quiz = englishQuizOwnedBy(owner);
        Question question = questionLocalizedIn(EN);
        quiz.addQuestion(question.getId());
        when(quizRepository.findById(quiz.getId())).thenReturn(Optional.of(quiz));
        when(questionRepository.findAllById(List.of(question.getId()))).thenReturn(List.of(question));

        QuizView view = service.publish(owner, new PublishQuizCommand(quiz.getId()));

        assertThat(view.state()).isEqualTo(QuizState.PUBLISHED);
        ArgumentCaptor<QuizPublishedEvent> event = ArgumentCaptor.forClass(QuizPublishedEvent.class);
        verify(eventPublisher).publish(event.capture());
        assertThat(event.getValue().quizId()).isEqualTo(quiz.getId());
        assertThat(event.getValue().occurredAt()).isEqualTo(NOW);
    }

    @Test
    void shouldRejectQuestionMissingTheDefaultLanguage() {
        Quiz quiz = englishQuizOwnedBy(owner);
        Question kannadaOnly = questionLocalizedIn(KN);
        quiz.addQuestion(kannadaOnly.getId());
        when(quizRepository.findById(quiz.getId())).thenReturn(Optional.of(quiz));
        when(questionRepository.findAllById(List.of(kannadaOnly.getId())))
                .thenReturn(List.of(kannadaOnly));

        assertThatExceptionOfType(QuizNotPublishableException.class)
                .isThrownBy(() -> service.publish(owner, new PublishQuizCommand(quiz.getId())))
                .withMessageContaining(kannadaOnly.getId().toString());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void shouldRejectQuizWithoutQuestions() {
        Quiz quiz = englishQuizOwnedBy(owner);
        when(quizRepository.findById(quiz.getId())).thenReturn(Optional.of(quiz));

        assertThatExceptionOfType(QuizNotPublishableException.class)
                .isThrownBy(() -> service.publish(owner, new PublishQuizCommand(quiz.getId())));
        verifyNoInteractions(eventPublisher);
        verify(questionRepository, org.mockito.Mockito.never()).findAllById(any());
    }
}
