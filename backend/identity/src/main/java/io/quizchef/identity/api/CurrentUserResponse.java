package io.quizchef.identity.api;

import io.quizchef.identity.application.CurrentUserView;
import io.quizchef.identity.domain.IdentityType;
import io.quizchef.identity.domain.Permission;
import io.quizchef.identity.domain.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;
import java.util.UUID;

/**
 * The authenticated caller's identity, roles, and derived permissions.
 */
public record CurrentUserResponse(

        @Schema(example = "1557e119-29db-4e3a-b3ed-ea47233e8a59")
        UUID identityId,

        @Schema(example = "REGISTERED")
        IdentityType identityType,

        @Schema(example = "[\"USER\"]")
        Set<Role> roles,

        @Schema(example = "[\"QUIZ_VIEW\", \"USER_PROFILE_READ\", \"USER_PROFILE_UPDATE\"]")
        Set<Permission> permissions,

        @Schema(description = "The caller's own display name; null for identities without a profile",
                example = "Aman")
        String displayName,

        @Schema(description = "The caller's own email; null for identities without a profile",
                example = "aman@example.com")
        String email
) {

    static CurrentUserResponse from(CurrentUserView view) {
        return new CurrentUserResponse(
                view.identityId(), view.identityType(), view.roles(), view.permissions(),
                view.displayName(), view.email());
    }
}
