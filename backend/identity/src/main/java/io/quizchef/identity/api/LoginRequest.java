package io.quizchef.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Login request. Deliberately no password-policy constraints here: login
 * must not reveal the current policy, only registration enforces it.
 */
public record LoginRequest(

        @Schema(example = "aman@example.com")
        @NotBlank
        @Email
        String email,

        @Schema(example = "StrongPassword@123")
        @NotBlank
        String password
) {

    /**
     * The raw password must never reach logs, even accidentally.
     */
    @Override
    public String toString() {
        return "LoginRequest[email=%s, password=<redacted>]".formatted(email);
    }
}
