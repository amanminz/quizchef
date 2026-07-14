package io.quizchef.identity.api;

import io.quizchef.common.api.ApiError;
import io.quizchef.identity.application.AuthenticateIdentityApplicationService;
import io.quizchef.identity.application.AuthenticateIdentityCommand;
import io.quizchef.identity.application.AuthenticationResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Login endpoint: validates the request, delegates to the application
 * service, and maps {@link AuthenticationResult} to HTTP. No business
 * logic lives here.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Identity registration and login")
public class AuthenticationController {

    private final AuthenticateIdentityApplicationService authenticateIdentityApplicationService;

    public AuthenticationController(
            AuthenticateIdentityApplicationService authenticateIdentityApplicationService) {
        this.authenticateIdentityApplicationService = authenticateIdentityApplicationService;
    }

    @PostMapping("/login")
    @Operation(
            summary = "Authenticate with email and password",
            description = "Verifies credentials, revokes previous login sessions (single active "
                    + "session per identity), and returns a session-bound JWT.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authenticated; token returned"),
            @ApiResponse(responseCode = "400", description = "Request validation failed",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Invalid email or password",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public LoginResponse login(@Valid @RequestBody LoginRequest request,
                               HttpServletRequest httpRequest) {
        AuthenticationResult result = authenticateIdentityApplicationService.authenticate(
                new AuthenticateIdentityCommand(
                        request.email(),
                        request.password(),
                        httpRequest.getHeader(HttpHeaders.USER_AGENT),
                        httpRequest.getRemoteAddr()));
        return LoginResponse.from(result);
    }
}
