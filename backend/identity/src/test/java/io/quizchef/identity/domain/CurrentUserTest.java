package io.quizchef.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CurrentUserTest {

    @Test
    void anonymousUserHasNoIdentityAndNoRoles() {
        CurrentUser user = CurrentUser.anonymous();

        assertThat(user.authenticated()).isFalse();
        assertThat(user.identityId()).isNull();
        assertThat(user.identityType()).isNull();
        assertThat(user.roles()).isEmpty();
        assertThat(user.isGuest()).isFalse();
    }

    @Test
    void authenticatedUserExposesIdentityAndRoles() {
        UUID identityId = UUID.randomUUID();

        CurrentUser user = CurrentUser.authenticated(
                identityId, IdentityType.REGISTERED, Set.of(Role.USER, Role.QUIZ_MASTER));

        assertThat(user.authenticated()).isTrue();
        assertThat(user.identityId()).isEqualTo(identityId);
        assertThat(user.hasRole(Role.QUIZ_MASTER)).isTrue();
        assertThat(user.hasRole(Role.ADMIN)).isFalse();
    }

    @Test
    void guestUserIsAuthenticatedWithoutRoles() {
        CurrentUser user = CurrentUser.authenticated(UUID.randomUUID(), IdentityType.GUEST, Set.of());

        assertThat(user.isGuest()).isTrue();
        assertThat(user.roles()).isEmpty();
    }

    @Test
    void authenticatedUserRequiresIdentityId() {
        assertThatNullPointerException()
                .isThrownBy(() -> CurrentUser.authenticated(null, IdentityType.REGISTERED, Set.of()));
    }

    @Test
    void authenticatedUserExposesIdentityReference() {
        UUID identityId = UUID.randomUUID();
        CurrentUser user = CurrentUser.authenticated(identityId, IdentityType.REGISTERED, Set.of(Role.USER));

        assertThat(user.reference().identityId()).isEqualTo(identityId);
        assertThat(user.reference().identityType()).isEqualTo(IdentityType.REGISTERED);
    }

    @Test
    void anonymousUserHasNoIdentityReference() {
        assertThatThrownBy(() -> CurrentUser.anonymous().reference())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rolesAreImmutable() {
        CurrentUser user = CurrentUser.authenticated(UUID.randomUUID(), IdentityType.REGISTERED, Set.of(Role.USER));

        assertThatThrownBy(() -> user.roles().add(Role.ADMIN))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
