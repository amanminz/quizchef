package io.quizchef.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.quizchef.common.event.DomainEventPublisher;
import io.quizchef.identity.domain.Credentials;
import io.quizchef.identity.domain.Identity;
import io.quizchef.identity.domain.IdentityType;
import io.quizchef.identity.domain.PasswordHasher;
import io.quizchef.identity.domain.UserProfile;
import io.quizchef.identity.domain.event.IdentityRegisteredEvent;
import io.quizchef.identity.domain.exception.DuplicateEmailException;
import io.quizchef.identity.infrastructure.persistence.CredentialsRepository;
import io.quizchef.identity.infrastructure.persistence.IdentityRepository;
import io.quizchef.identity.infrastructure.persistence.UserProfileRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class RegisterIdentityApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-14T10:00:00Z");

    @Mock
    private IdentityRepository identityRepository;

    @Mock
    private CredentialsRepository credentialsRepository;

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private PasswordHasher passwordHasher;

    @Mock
    private DomainEventPublisher eventPublisher;

    private RegisterIdentityApplicationService service;

    @BeforeEach
    void createService() {
        service = new RegisterIdentityApplicationService(
                identityRepository, credentialsRepository, userProfileRepository,
                passwordHasher, eventPublisher, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void shouldRegisterIdentityWithHashedPasswordAndPublishEvent() {
        when(userProfileRepository.existsByEmail("aman@example.com")).thenReturn(false);
        when(passwordHasher.hash("StrongPassword@123")).thenReturn("$argon2id$hashed");

        RegisteredIdentity registered = service.register(new RegisterIdentityCommand(
                "Aman Minz", "aman@example.com", "StrongPassword@123", "+919999999999"));

        ArgumentCaptor<Identity> identityCaptor = ArgumentCaptor.forClass(Identity.class);
        verify(identityRepository).save(identityCaptor.capture());
        Identity identity = identityCaptor.getValue();
        assertThat(identity.getIdentityType()).isEqualTo(IdentityType.REGISTERED);

        ArgumentCaptor<Credentials> credentialsCaptor = ArgumentCaptor.forClass(Credentials.class);
        verify(credentialsRepository).save(credentialsCaptor.capture());
        assertThat(credentialsCaptor.getValue().getPasswordHash()).isEqualTo("$argon2id$hashed");
        assertThat(credentialsCaptor.getValue().getIdentityId()).isEqualTo(identity.getId());

        ArgumentCaptor<UserProfile> profileCaptor = ArgumentCaptor.forClass(UserProfile.class);
        verify(userProfileRepository).saveAndFlush(profileCaptor.capture());
        UserProfile profile = profileCaptor.getValue();
        assertThat(profile.getEmail()).isEqualTo("aman@example.com");
        assertThat(profile.getPhoneNumber()).isEqualTo("+919999999999");

        ArgumentCaptor<IdentityRegisteredEvent> eventCaptor =
                ArgumentCaptor.forClass(IdentityRegisteredEvent.class);
        verify(eventPublisher).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().identity().identityId()).isEqualTo(identity.getId());
        assertThat(eventCaptor.getValue().occurredAt()).isEqualTo(NOW);

        assertThat(registered.identityId()).isEqualTo(identity.getId());
        assertThat(registered.email()).isEqualTo("aman@example.com");
    }

    @Test
    void shouldNormalizeEmailBeforeUniquenessCheckAndPersistence() {
        when(userProfileRepository.existsByEmail("aman@example.com")).thenReturn(false);
        when(passwordHasher.hash(any())).thenReturn("hash");

        RegisteredIdentity registered = service.register(new RegisterIdentityCommand(
                "Aman", "  AMAN@Example.COM ", "StrongPassword@123", null));

        verify(userProfileRepository).existsByEmail("aman@example.com");
        assertThat(registered.email()).isEqualTo("aman@example.com");
    }

    @Test
    void shouldRejectDuplicateEmailWithoutCreatingAnything() {
        when(userProfileRepository.existsByEmail("aman@example.com")).thenReturn(true);

        assertThatExceptionOfType(DuplicateEmailException.class)
                .isThrownBy(() -> service.register(new RegisterIdentityCommand(
                        "Aman", "aman@example.com", "StrongPassword@123", null)));

        verify(identityRepository, never()).save(any());
        verify(credentialsRepository, never()).save(any());
        verify(userProfileRepository, never()).saveAndFlush(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void shouldTranslateConcurrentDuplicateIntoDuplicateEmailException() {
        when(userProfileRepository.existsByEmail("aman@example.com")).thenReturn(false);
        when(passwordHasher.hash(any())).thenReturn("hash");
        when(userProfileRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatExceptionOfType(DuplicateEmailException.class)
                .isThrownBy(() -> service.register(new RegisterIdentityCommand(
                        "Aman", "aman@example.com", "StrongPassword@123", null)));

        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void shouldNotExposePasswordInCommandToString() {
        var command = new RegisterIdentityCommand(
                "Aman", "aman@example.com", "StrongPassword@123", null);

        assertThat(command.toString())
                .doesNotContain("StrongPassword@123")
                .contains("<redacted>");
    }
}
