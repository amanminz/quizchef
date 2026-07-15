package io.quizchef.quiz.application;

import static io.quizchef.quiz.application.QuestionApplicationTestFixtures.englishQuestionOwnedBy;
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
import io.quizchef.identity.domain.IdentityType;
import io.quizchef.identity.domain.Permission;
import io.quizchef.identity.domain.Role;
import io.quizchef.quiz.domain.Question;
import io.quizchef.quiz.domain.QuestionState;
import io.quizchef.quiz.domain.event.QuestionPublishedEvent;
import io.quizchef.quiz.domain.exception.QuestionArchivedException;
import io.quizchef.quiz.domain.exception.QuestionNotFoundException;
import io.quizchef.quiz.domain.exception.QuestionNotPublishableException;
import io.quizchef.quiz.infrastructure.persistence.QuestionRepository;
import io.quizchef.quiz.infrastructure.persistence.TagRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PublishQuestionApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-15T10:00:00Z");

    private final QuestionRepository questionRepository = mock(QuestionRepository.class);
    private final TagRepository tagRepository = mock(TagRepository.class);
    private final AuthorizationService authorizationService = mock(AuthorizationService.class);
    private final DomainEventPublisher eventPublisher = mock(DomainEventPublisher.class);
    private final PublishQuestionApplicationService service = new PublishQuestionApplicationService(
            questionRepository, tagRepository, authorizationService, eventPublisher,
            Clock.fixed(NOW, ZoneOffset.UTC));

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
    void shouldPublishDraftAndPublishEvent() {
        Question question = storedQuestion(englishQuestionOwnedBy(owner));

        QuestionView view = service.publish(owner, new PublishQuestionCommand(question.getId()));

        assertThat(view.state()).isEqualTo(QuestionState.PUBLISHED);
        verify(authorizationService).authorize(owner, Permission.QUIZ_EDIT);
        ArgumentCaptor<QuestionPublishedEvent> event =
                ArgumentCaptor.forClass(QuestionPublishedEvent.class);
        verify(eventPublisher).publish(event.capture());
        assertThat(event.getValue().questionId()).isEqualTo(question.getId());
        assertThat(event.getValue().occurredAt()).isEqualTo(NOW);
    }

    @Test
    void shouldRejectPublishingTwice() {
        Question question = storedQuestion(englishQuestionOwnedBy(owner));
        question.publish();

        assertThatExceptionOfType(QuestionNotPublishableException.class)
                .isThrownBy(() -> service.publish(owner, new PublishQuestionCommand(question.getId())));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void shouldRejectPublishingArchivedQuestion() {
        Question question = storedQuestion(englishQuestionOwnedBy(owner));
        question.publish();
        question.archive();

        assertThatExceptionOfType(QuestionArchivedException.class)
                .isThrownBy(() -> service.publish(owner, new PublishQuestionCommand(question.getId())));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void nonOwnersCannotPublish() {
        Question question = storedQuestion(englishQuestionOwnedBy(owner));

        assertThatExceptionOfType(QuestionNotFoundException.class)
                .isThrownBy(() -> service.publish(stranger, new PublishQuestionCommand(question.getId())));
        verifyNoInteractions(eventPublisher);
    }
}
