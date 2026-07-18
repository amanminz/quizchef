package io.quizchef.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.quizchef.common.event.DomainEventPublisher;
import io.quizchef.common.exception.ForbiddenException;
import io.quizchef.common.exception.UnauthorizedException;
import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.identity.domain.Identity;
import io.quizchef.identity.domain.IdentityType;
import io.quizchef.identity.domain.Permission;
import io.quizchef.identity.domain.Role;
import io.quizchef.identity.domain.event.HostAccessGrantedEvent;
import io.quizchef.identity.infrastructure.persistence.IdentityRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RequestHostAccessApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-18T10:00:00Z");

    private final IdentityRepository identityRepository = mock(IdentityRepository.class);
    private final DomainEventPublisher eventPublisher = mock(DomainEventPublisher.class);
    private final AuthorizationService authorizationService =
            new AuthorizationService(eventPublisher, Clock.fixed(NOW, ZoneOffset.UTC));
    private final RequestHostAccessApplicationService service = new RequestHostAccessApplicationService(
            identityRepository, authorizationService, eventPublisher, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void grantsQuizMasterDurablyAndAnnouncesIt() {
        Identity identity = Identity.registered();
        when(identityRepository.findById(identity.getId())).thenReturn(Optional.of(identity));
        CurrentUser caller = CurrentUser.authenticated(
                identity.getId(), IdentityType.REGISTERED, Set.of(Role.USER));

        HostAccessView view = service.request(caller);

        assertThat(view.status()).isEqualTo(HostAccessView.HostAccessStatus.GRANTED);
        assertThat(view.roles()).containsExactlyInAnyOrder(Role.USER, Role.QUIZ_MASTER);
        assertThat(view.permissions()).contains(Permission.QUIZ_HOST, Permission.QUIZ_CREATE);
        assertThat(identity.hasRole(Role.QUIZ_MASTER)).isTrue();
        verify(identityRepository).save(identity);
        verify(eventPublisher).publish(any(HostAccessGrantedEvent.class));
    }

    @Test
    void repeatRequestIsIdempotentAndSilent() {
        Identity identity = Identity.registered();
        identity.grantRole(Role.QUIZ_MASTER);
        when(identityRepository.findById(identity.getId())).thenReturn(Optional.of(identity));
        CurrentUser caller = CurrentUser.authenticated(
                identity.getId(), IdentityType.REGISTERED, Set.of(Role.USER, Role.QUIZ_MASTER));

        HostAccessView view = service.request(caller);

        assertThat(view.status()).isEqualTo(HostAccessView.HostAccessStatus.GRANTED);
        verify(identityRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any(HostAccessGrantedEvent.class));
    }

    @Test
    void refusesAnonymousCallers() {
        assertThatExceptionOfType(UnauthorizedException.class)
                .isThrownBy(() -> service.request(CurrentUser.anonymous()));
    }

    @Test
    void refusesGuestsThroughTheOrdinaryPermissionPath() {
        // Guests hold no roles, so USER_PROFILE_UPDATE is denied — no
        // special-case guest check exists or is needed.
        CurrentUser guest = CurrentUser.authenticated(
                Identity.guest().getId(), IdentityType.GUEST, Set.of());

        assertThatExceptionOfType(ForbiddenException.class)
                .isThrownBy(() -> service.request(guest));
    }
}
