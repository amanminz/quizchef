package io.quizchef.identity.api;

import io.quizchef.common.api.ApiError;
import io.quizchef.identity.application.RegisterIdentityApplicationService;
import io.quizchef.identity.application.RegisterIdentityCommand;
import io.quizchef.identity.application.RegisteredIdentity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Registration endpoint: validates the request, delegates to the application
 * service, and translates the outcome to HTTP. No business logic lives here.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Identity registration; login arrives in a later release")
public class RegistrationController {

    private final RegisterIdentityApplicationService registerIdentityApplicationService;

    public RegistrationController(RegisterIdentityApplicationService registerIdentityApplicationService) {
        this.registerIdentityApplicationService = registerIdentityApplicationService;
    }

    @PostMapping("/register")
    @Operation(
            summary = "Register a new identity",
            description = "Creates a registered identity with credentials and profile. "
                    + "Does not log the user in and returns no token.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Identity registered; Location points to the new resource"),
            @ApiResponse(responseCode = "400", description = "Request validation failed",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Email address is already registered",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<RegisterIdentityResponse> register(
            @Valid @RequestBody RegisterIdentityRequest request) {
        RegisteredIdentity registered = registerIdentityApplicationService.register(
                new RegisterIdentityCommand(
                        request.displayName(), request.email(), request.password(), request.phoneNumber()));
        return ResponseEntity
                .created(URI.create("/api/v1/identities/" + registered.identityId()))
                .body(RegisterIdentityResponse.from(registered));
    }
}
