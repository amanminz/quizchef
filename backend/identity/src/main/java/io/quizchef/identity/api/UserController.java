package io.quizchef.identity.api;

import io.quizchef.common.api.ApiError;
import io.quizchef.identity.application.CurrentUserApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
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

    public UserController(CurrentUserApplicationService currentUserApplicationService) {
        this.currentUserApplicationService = currentUserApplicationService;
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
}
