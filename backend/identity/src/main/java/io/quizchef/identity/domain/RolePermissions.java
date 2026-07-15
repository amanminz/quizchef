package io.quizchef.identity.domain;

import java.util.Map;
import java.util.Set;

/**
 * The explicit, code-defined role-to-permission mapping (RFC-002).
 *
 * <p>Deliberately not a database-backed policy engine: a code-defined
 * mapping is easy to understand and test, keeps authorization
 * deterministic, and avoids administrative complexity the platform does
 * not need yet. When organizations, custom roles, or multi-tenancy
 * arrive, this evolves into a persisted policy engine.
 *
 * <p>Roles are additive: an identity's permissions are the union over all
 * roles it holds.
 */
public final class RolePermissions {

    private static final Map<Role, Set<Permission>> MAPPING = Map.of(
            Role.ADMIN, Set.of(
                    Permission.QUIZ_VIEW,
                    Permission.QUIZ_CREATE,
                    Permission.QUIZ_EDIT,
                    Permission.QUIZ_DELETE,
                    Permission.QUIZ_HOST,
                    Permission.USER_PROFILE_READ,
                    Permission.USER_PROFILE_UPDATE),
            Role.QUIZ_MASTER, Set.of(
                    Permission.QUIZ_VIEW,
                    Permission.QUIZ_CREATE,
                    Permission.QUIZ_EDIT,
                    Permission.QUIZ_HOST),
            Role.USER, Set.of(
                    Permission.QUIZ_VIEW,
                    Permission.USER_PROFILE_READ,
                    Permission.USER_PROFILE_UPDATE));

    public static Set<Permission> permissionsOf(Role role) {
        return MAPPING.get(role);
    }

    private RolePermissions() {
    }
}
