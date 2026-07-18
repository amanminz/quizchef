package io.quizchef.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import org.junit.jupiter.api.Test;

class IdentityTest {

    @Test
    void shouldCreateActiveRegisteredIdentity() {
        Identity identity = Identity.registered();

        assertThat(identity.getId()).isNotNull();
        assertThat(identity.getIdentityType()).isEqualTo(IdentityType.REGISTERED);
        assertThat(identity.getStatus()).isEqualTo(IdentityStatus.ACTIVE);
        assertThat(identity.isActive()).isTrue();
        assertThat(identity.isGuest()).isFalse();
    }

    @Test
    void shouldCreateActiveGuestIdentity() {
        Identity identity = Identity.guest();

        assertThat(identity.getIdentityType()).isEqualTo(IdentityType.GUEST);
        assertThat(identity.isGuest()).isTrue();
        assertThat(identity.isActive()).isTrue();
    }

    @Test
    void shouldDisableAndReenableIdentity() {
        Identity identity = Identity.registered();

        identity.disable();
        assertThat(identity.isActive()).isFalse();
        assertThat(identity.getStatus()).isEqualTo(IdentityStatus.DISABLED);

        identity.enable();
        assertThat(identity.isActive()).isTrue();
    }

    @Test
    void shouldAssignDistinctIdsToEachIdentity() {
        assertThat(Identity.registered().getId()).isNotEqualTo(Identity.registered().getId());
    }

    @Test
    void shouldExposeReferenceCarryingOnlyIdAndType() {
        Identity identity = Identity.guest();

        IdentityReference reference = identity.reference();

        assertThat(reference.identityId()).isEqualTo(identity.getId());
        assertThat(reference.identityType()).isEqualTo(IdentityType.GUEST);
        assertThat(reference.isGuest()).isTrue();
    }

    @Test
    void shouldSeedEveryRegisteredIdentityWithTheUserRole() {
        assertThat(Identity.registered().roles()).containsExactly(Role.USER);
        assertThat(Identity.registered().hasRole(Role.USER)).isTrue();
    }

    @Test
    void shouldSeedGuestsWithNoRoles() {
        assertThat(Identity.guest().roles()).isEmpty();
    }

    @Test
    void shouldGrantRolesAdditivelyAndIdempotently() {
        Identity identity = Identity.registered();

        assertThat(identity.grantRole(Role.QUIZ_MASTER)).as("first grant changes state").isTrue();
        assertThat(identity.grantRole(Role.QUIZ_MASTER)).as("repeat grant is a no-op").isFalse();
        assertThat(identity.roles()).containsExactlyInAnyOrder(Role.USER, Role.QUIZ_MASTER);
    }

    @Test
    void shouldRefuseGrantingRolesToGuests() {
        assertThatIllegalStateException()
                .isThrownBy(() -> Identity.guest().grantRole(Role.QUIZ_MASTER));
    }
}
