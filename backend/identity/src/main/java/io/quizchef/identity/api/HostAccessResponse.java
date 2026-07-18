package io.quizchef.identity.api;

import io.quizchef.identity.application.HostAccessView;
import io.quizchef.identity.domain.Permission;
import io.quizchef.identity.domain.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;

/**
 * The outcome of a host-access request: today always GRANTED (automatic
 * self-service promotion — RFC-002 records the product rule), with the
 * caller's resulting roles and permissions so a client updates itself
 * without another round trip. PENDING and DENIED are reserved for a
 * future approval gate; the shape will not change when one arrives.
 */
public record HostAccessResponse(
        @Schema(example = "GRANTED")
        HostAccessView.HostAccessStatus status,
        @Schema(example = "[\"USER\", \"QUIZ_MASTER\"]")
        Set<Role> roles,
        @Schema(example = "[\"QUIZ_VIEW\", \"QUIZ_CREATE\", \"QUIZ_EDIT\", \"QUIZ_HOST\", "
                + "\"USER_PROFILE_READ\", \"USER_PROFILE_UPDATE\"]")
        Set<Permission> permissions
) {

    static HostAccessResponse from(HostAccessView view) {
        return new HostAccessResponse(view.status(), view.roles(), view.permissions());
    }
}
