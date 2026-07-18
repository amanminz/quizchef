package io.quizchef.identity.api;

import io.quizchef.common.api.ApiError;
import io.quizchef.identity.application.CurrentUserApplicationService;
import io.quizchef.identity.application.RequestHostAccessApplicationService;
import io.quizchef.identity.domain.CurrentUserProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The authenticated user's own resources. Authorization happens in the
 * application service — never here.
 */
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "The authenticated user's own resources")
public class UserController {

    private final CurrentUserApplicationService currentUserApplicationService;
    private final RequestHostAccessApplicationService requestHostAccessApplicationService;
    private final CurrentUserProvider currentUserProvider;

    public UserController(CurrentUserApplicationService currentUserApplicationService,
                          RequestHostAccessApplicationService requestHostAccessApplicationService,
                          CurrentUserProvider currentUserProvider) {
        this.currentUserApplicationService = currentUserApplicationService;
        this.requestHostAccessApplicationService = requestHostAccessApplicationService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/me")
    @Operation(
            summary = "Who am I?",
            description = "Returns the authenticated identity with its roles and derived permissions. "
                    + "Requires a bearer token from POST /api/v1/auth/login.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The authenticated identity"),
            @ApiResponse(responseCode = "401", description = "Missing, invalid, or revoked token",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Authenticated but lacking USER_PROFILE_READ",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public CurrentUserResponse me() {
        return CurrentUserResponse.from(currentUserApplicationService.currentUser());
    }

    @PostMapping("/me/host-access")
    @Operation(
            summary = "Request host access",
            description = "Grants the caller the QUIZ_MASTER role, durably and idempotently — the "
                    + "product rule is automatic self-service promotion (RFC-002); PENDING/DENIED "
                    + "are reserved for a future approval gate. Because request-time authorization "
                    + "reads persisted roles, the grant takes effect on the very next request with "
                    + "the same token — no new login needed. Guests cannot request host access.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Host access granted (idempotent)"),
            @ApiResponse(responseCode = "401", description = "Missing, invalid, or revoked token",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "The caller may not modify their account "
                    + "(guests)", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public HostAccessResponse requestHostAccess() {
        return HostAccessResponse.from(
                requestHostAccessApplicationService.request(currentUserProvider.currentUser()));
    }
}
