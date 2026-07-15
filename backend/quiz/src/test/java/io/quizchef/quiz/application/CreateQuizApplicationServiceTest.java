package io.quizchef.quiz.application;

import static io.quizchef.quiz.application.QuizApplicationTestFixtures.quizMaster;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.quizchef.common.event.DomainEventPublisher;
import io.quizchef.common.exception.ForbiddenException;
import io.quizchef.identity.application.AuthorizationService;
import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.identity.domain.Permission;
import io.quizchef.quiz.domain.QuizState;
import io.quizchef.quiz.domain.QuizVisibility;
import io.quizchef.quiz.domain.event.QuizCreatedEvent;
import io.quizchef.quiz.infrastructure.persistence.QuizRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CreateQuizApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-15T10:00:00Z");

    private final QuizRepository quizRepository = mock(QuizRepository.class);
    private final AuthorizationService authorizationService = mock(AuthorizationService.class);
    private final DomainEventPublisher eventPublisher = mock(DomainEventPublisher.class);
    private final CreateQuizApplicationService service = new CreateQuizApplicationService(
            quizRepository, authorizationService, eventPublisher, Clock.fixed(NOW, ZoneOffset.UTC));

    private final CurrentUser caller = quizMaster(UUID.randomUUID());

    private static CreateQuizCommand command(String defaultLanguage, String localizationLanguage) {
        return new CreateQuizCommand(defaultLanguage, QuizVisibility.UNLISTED,
                new QuizLocalizationCommand(localizationLanguage, "Bible Quiz", "Sunday Youth Fellowship"),
                new QuizSettingsCommand(false, false, 45, true, true));
    }

    @Test
    void shouldCreateDraftQuizOwnedByCaller() {
        QuizView view = service.create(caller, command("en", "en"));

        assertThat(view.state()).isEqualTo(QuizState.DRAFT);
        assertThat(view.visibility()).isEqualTo(QuizVisibility.UNLISTED);
        assertThat(view.ownerIdentityId()).isEqualTo(caller.identityId());
        assertThat(view.defaultLanguage().value()).isEqualTo("en");
        assertThat(view.settings().questionTimeLimitSeconds()).isEqualTo(45);
        verify(authorizationService).authorize(caller, Permission.QUIZ_CREATE);
        verify(quizRepository).save(any());

        ArgumentCaptor<QuizCreatedEvent> event = ArgumentCaptor.forClass(QuizCreatedEvent.class);
        verify(eventPublisher).publish(event.capture());
        assertThat(event.getValue().quizId()).isEqualTo(view.id());
        assertThat(event.getValue().owner()).isEqualTo(caller.reference());
        assertThat(event.getValue().occurredAt()).isEqualTo(NOW);
    }

    @Test
    void shouldApplyDefaultsWhenVisibilityAndSettingsOmitted() {
        QuizView view = service.create(caller, new CreateQuizCommand("en", null,
                new QuizLocalizationCommand("en", "Bible Quiz", null), null));

        assertThat(view.visibility()).isEqualTo(QuizVisibility.PRIVATE);
        assertThat(view.settings().questionTimeLimitSeconds()).isEqualTo(30);
    }

    @Test
    void shouldRejectLocalizationNotInDefaultLanguage() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.create(caller, command("kn", "en")));
        verifyNoInteractions(quizRepository, eventPublisher);
    }

    @Test
    void deniedAuthorizationCreatesNothing() {
        doThrow(new ForbiddenException()).when(authorizationService)
                .authorize(eq(caller), eq(Permission.QUIZ_CREATE));

        assertThatExceptionOfType(ForbiddenException.class)
                .isThrownBy(() -> service.create(caller, command("en", "en")));
        verifyNoInteractions(quizRepository, eventPublisher);
    }
}
