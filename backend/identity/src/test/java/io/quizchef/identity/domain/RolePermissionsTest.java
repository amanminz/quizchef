package io.quizchef.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Pins the explicit role-to-permission matrix from RFC-002. A failing
 * test here means the authorization policy changed — that must always be
 * a conscious, reviewed decision.
 */
class RolePermissionsTest {

    @Test
    void adminHoldsEveryPermission() {
        assertThat(RolePermissions.permissionsOf(Role.ADMIN))
                .containsExactlyInAnyOrder(Permission.values());
    }

    @Test
    void quizMasterManagesAndHostsQuizzesButDoesNotDelete() {
        assertThat(RolePermissions.permissionsOf(Role.QUIZ_MASTER))
                .containsExactlyInAnyOrder(
                        Permission.QUIZ_VIEW,
                        Permission.QUIZ_CREATE,
                        Permission.QUIZ_EDIT,
                        Permission.QUIZ_HOST);
    }

    @Test
    void userViewsQuizzesAndManagesOwnProfile() {
        assertThat(RolePermissions.permissionsOf(Role.USER))
                .containsExactlyInAnyOrder(
                        Permission.QUIZ_VIEW,
                        Permission.USER_PROFILE_READ,
                        Permission.USER_PROFILE_UPDATE);
    }

    @Test
    void everyRoleHasAMapping() {
        for (Role role : Role.values()) {
            assertThat(RolePermissions.permissionsOf(role))
                    .as("role %s must have an explicit permission mapping", role)
                    .isNotNull()
                    .isNotEmpty();
        }
    }
}
