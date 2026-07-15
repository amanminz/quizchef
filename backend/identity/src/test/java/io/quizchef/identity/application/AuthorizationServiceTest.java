package io.quizchef.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.quizchef.common.event.DomainEventPublisher;
import io.quizchef.common.exception.ForbiddenException;
import io.quizchef.common.exception.UnauthorizedException;
import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.identity.domain.IdentityType;
import io.quizchef.identity.domain.Permission;
import io.quizchef.identity.domain.Role;
import io.quizchef.identity.domain.event.IdentityAuthorizedEvent;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthorizationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-14T10:00:00Z");

    @Mock
    private DomainEventPublisher eventPublisher;

    private AuthorizationService service;

    @BeforeEach
    void createService() {
        service = new AuthorizationService(eventPublisher, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private CurrentUser userWith(Role... roles) {
        return CurrentUser.authenticated(UUID.randomUUID(), IdentityType.REGISTERED, Set.of(roles));
    }

    @Test
    void shouldGrantPermissionAndPublishEvent() {
        CurrentUser user = userWith(Role.USER);

        service.authorize(user, Permission.USER_PROFILE_READ);

        ArgumentCaptor<IdentityAuthorizedEvent> eventCaptor =
                ArgumentCaptor.forClass(IdentityAuthorizedEvent.class);
        verify(eventPublisher).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().identity().identityId()).isEqualTo(user.identityId());
        assertThat(eventCaptor.getValue().permission()).isEqualTo(Permission.USER_PROFILE_READ);
        assertThat(eventCaptor.getValue().occurredAt()).isEqualTo(NOW);
    }

    @Test
    void shouldDenyMissingPermissionWithoutPublishingEvent() {
        CurrentUser user = userWith(Role.USER);

        assertThatExceptionOfType(ForbiddenException.class)
                .isThrownBy(() -> service.authorize(user, Permission.QUIZ_CREATE))
                .satisfies(exception ->
                        assertThat(exception.errorCode()).isEqualTo("auth.permission.denied"));

        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void shouldRejectAnonymousUserAsUnauthorized() {
        assertThatExceptionOfType(UnauthorizedException.class)
                .isThrownBy(() -> service.authorize(CurrentUser.anonymous(), Permission.QUIZ_VIEW));

        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void shouldTreatRolesAsAdditive() {
        CurrentUser quizMasterAndUser = userWith(Role.QUIZ_MASTER, Role.USER);

        assertThat(service.isGranted(quizMasterAndUser, Permission.QUIZ_CREATE)).isTrue();
        assertThat(service.isGranted(quizMasterAndUser, Permission.USER_PROFILE_READ)).isTrue();
        assertThat(service.isGranted(quizMasterAndUser, Permission.QUIZ_DELETE)).isFalse();
    }

    @Test
    void shouldDeriveUnionOfPermissionsAcrossRoles() {
        assertThat(service.permissionsOf(userWith(Role.QUIZ_MASTER, Role.USER)))
                .containsExactlyInAnyOrder(
                        Permission.QUIZ_VIEW,
                        Permission.QUIZ_CREATE,
                        Permission.QUIZ_EDIT,
                        Permission.QUIZ_HOST,
                        Permission.USER_PROFILE_READ,
                        Permission.USER_PROFILE_UPDATE);
    }

    @Test
    void shouldGrantNothingToAnonymousOrRoleLessUsers() {
        assertThat(service.permissionsOf(CurrentUser.anonymous())).isEmpty();
        assertThat(service.permissionsOf(
                CurrentUser.authenticated(UUID.randomUUID(), IdentityType.GUEST, Set.of())))
                .isEmpty();
    }
}
