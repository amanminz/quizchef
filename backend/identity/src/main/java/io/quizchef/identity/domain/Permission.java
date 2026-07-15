package io.quizchef.identity.domain;

/**
 * Everything an identity may be allowed to do.
 *
 * <p>Deliberately small: permissions exist only for functionality that
 * exists. Permissions are never persisted — they are derived from roles
 * through {@link RolePermissions}.
 */
public enum Permission {
    QUIZ_VIEW,
    QUIZ_CREATE,
    QUIZ_EDIT,
    QUIZ_DELETE,
    QUIZ_HOST,
    USER_PROFILE_READ,
    USER_PROFILE_UPDATE
}
