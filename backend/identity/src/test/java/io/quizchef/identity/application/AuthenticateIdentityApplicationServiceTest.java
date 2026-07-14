package io.quizchef.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.quizchef.common.event.DomainEventPublisher;
import io.quizchef.identity.domain.Credentials;
import io.quizchef.identity.domain.Identity;
import io.quizchef.identity.domain.IdentitySession;
import io.quizchef.identity.domain.IdentityType;
import io.quizchef.identity.domain.PasswordHasher;
import io.quizchef.identity.domain.Role;
import io.quizchef.identity.domain.UserProfile;
import io.quizchef.identity.domain.event.IdentityAuthenticatedEvent;
import io.quizchef.identity.domain.exception.InvalidCredentialsException;
import io.quizchef.identity.infrastructure.jwt.IssuedToken;
import io.quizchef.identity.infrastructure.jwt.JwtTokenGenerator;
import io.quizchef.identity.infrastructure.persistence.CredentialsRepository;
import io.quizchef.identity.infrastructure.persistence.IdentityRepository;
import io.quizchef.identity.infrastructure.persistence.IdentitySessionRepository;
import io.quizchef.identity.infrastructure.persistence.UserProfileRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthenticateIdentityApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-14T10:00:00Z");
    private static final Instant EXPIRY = NOW.plus(Duration.ofMinutes(15));

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private CredentialsRepository credentialsRepository;

    @Mock
    private IdentityRepository identityRepository;

    @Mock
    private IdentitySessionRepository identitySessionRepository;

    @Mock
    private PasswordHasher passwordHasher;

    @Mock
    private JwtTokenGenerator tokenGenerator;

    @Mock
    private DomainEventPublisher eventPublisher;

    private AuthenticateIdentityApplicationService service;

    private Identity identity;
    private UserProfile profile;
    private Credentials credentials;

    @BeforeEach
    void createServiceAndRegisteredUser() {
        when(passwordHasher.hash("quizchef-timing-mask")).thenReturn("$argon2id$timing-mask");
        service = new AuthenticateIdentityApplicationService(
                userProfileRepository, credentialsRepository, identityRepository,
                identitySessionRepository, passwordHasher, tokenGenerator, eventPublisher,
                Clock.fixed(NOW, ZoneOffset.UTC));

        identity = Identity.registered();
        profile = UserProfile.create(identity.getId(), "Aman Minz", "aman@example.com");
        credentials = Credentials.create(identity.getId(), "$argon2id$stored-hash");

        when(userProfileRepository.findByEmail("aman@example.com")).thenReturn(Optional.of(profile));
        when(credentialsRepository.findByIdentityId(identity.getId())).thenReturn(Optional.of(credentials));
        when(identityRepository.findById(identity.getId())).thenReturn(Optional.of(identity));
        when(passwordHasher.matches("StrongPassword@123", "$argon2id$stored-hash")).thenReturn(true);
        when(identitySessionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(identitySessionRepository.findByIdentityIdAndRevokedFalse(identity.getId()))
                .thenReturn(List.of());
        when(tokenGenerator.generate(eq(identity.getId()), any(), eq(IdentityType.REGISTERED), any()))
                .thenReturn(new IssuedToken("issued-jwt", EXPIRY));
    }

    private AuthenticateIdentityCommand command() {
        return new AuthenticateIdentityCommand(
                "  AMAN@Example.COM ", "StrongPassword@123", "JUnit", "127.0.0.1");
    }

    @Test
    void shouldAuthenticateAndReturnSessionBoundToken() {
        AuthenticationResult result = service.authenticate(command());

        ArgumentCaptor<IdentitySession> sessionCaptor = ArgumentCaptor.forClass(IdentitySession.class);
        verify(identitySessionRepository).save(sessionCaptor.capture());
        IdentitySession session = sessionCaptor.getValue();
        assertThat(session.getIdentityId()).isEqualTo(identity.getId());
        assertThat(session.getUserAgent()).isEqualTo("JUnit");
        assertThat(session.getLastAuthenticatedAt()).isNotNull();

        verify(tokenGenerator).generate(
                identity.getId(), session.getId(), IdentityType.REGISTERED, Set.of(Role.USER));

        assertThat(result.identityId()).isEqualTo(identity.getId());
        assertThat(result.displayName()).isEqualTo("Aman Minz");
        assertThat(result.token()).isEqualTo("issued-jwt");
        assertThat(result.expiresAt()).isEqualTo(EXPIRY);
        assertThat(result.refreshToken()).isNull();
        assertThat(result.authorities()).containsExactly(Role.USER);
    }

    @Test
    void shouldRevokePreviousActiveSessions() {
        IdentitySession previousA = IdentitySession.start(identity.getId(), "old-a", "10.0.0.1", null);
        IdentitySession previousB = IdentitySession.start(identity.getId(), "old-b", "10.0.0.2", null);
        when(identitySessionRepository.findByIdentityIdAndRevokedFalse(identity.getId()))
                .thenReturn(List.of(previousA, previousB));

        service.authenticate(command());

        assertThat(previousA.isRevoked()).isTrue();
        assertThat(previousB.isRevoked()).isTrue();
        verify(identitySessionRepository).saveAll(List.of(previousA, previousB));
    }

    @Test
    void shouldPublishIdentityAuthenticatedEvent() {
        service.authenticate(command());

        ArgumentCaptor<IdentityAuthenticatedEvent> eventCaptor =
                ArgumentCaptor.forClass(IdentityAuthenticatedEvent.class);
        verify(eventPublisher).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().identity().identityId()).isEqualTo(identity.getId());
        assertThat(eventCaptor.getValue().occurredAt()).isEqualTo(NOW);
    }

    @Test
    void shouldRejectUnknownEmailAfterPayingHashingCost() {
        when(userProfileRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatExceptionOfType(InvalidCredentialsException.class)
                .isThrownBy(() -> service.authenticate(new AuthenticateIdentityCommand(
                        "nobody@example.com", "whatever-password", "JUnit", "127.0.0.1")));

        verify(passwordHasher).matches("whatever-password", "$argon2id$timing-mask");
        verify(identitySessionRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void shouldRejectWrongPasswordWithoutTouchingSessions() {
        when(passwordHasher.matches("WrongPassword", "$argon2id$stored-hash")).thenReturn(false);

        assertThatExceptionOfType(InvalidCredentialsException.class)
                .isThrownBy(() -> service.authenticate(new AuthenticateIdentityCommand(
                        "aman@example.com", "WrongPassword", "JUnit", "127.0.0.1")));

        verify(identitySessionRepository, never()).save(any());
        verify(identitySessionRepository, never()).findByIdentityIdAndRevokedFalse(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void shouldRejectDisabledIdentityWithSameError() {
        identity.disable();

        assertThatExceptionOfType(InvalidCredentialsException.class)
                .isThrownBy(() -> service.authenticate(command()))
                .satisfies(exception ->
                        assertThat(exception.errorCode()).isEqualTo("identity.credentials.invalid"));

        verify(identitySessionRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void shouldNotExposePasswordOrTokenInToStrings() {
        assertThat(command().toString())
                .doesNotContain("StrongPassword@123")
                .contains("<redacted>");
        assertThat(new AuthenticationResult(
                UUID.randomUUID(), "Aman", "secret-jwt", EXPIRY, null, Set.of(Role.USER)).toString())
                .doesNotContain("secret-jwt")
                .contains("<redacted>");
    }
}
