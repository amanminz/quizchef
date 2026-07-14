package io.quizchef.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Registration request. Layer-1 validation: shape only — business
 * preconditions and domain invariants are checked deeper in the flow.
 */
public record RegisterIdentityRequest(

        @Schema(example = "Aman Minz")
        @NotBlank
        @Size(min = 2, max = 50)
        String displayName,

        @Schema(example = "aman@example.com")
        @NotBlank
        @Email
        @Size(max = 320)
        String email,

        @Schema(example = "StrongPassword@123", description = "8-128 characters")
        @NotBlank
        @Size(min = 8, max = 128)
        String password,

        @Schema(example = "+919999999999", description = "Optional, E.164 format")
        @Pattern(regexp = "^\\+?[1-9][0-9]{6,14}$", message = "must be a valid international phone number")
        String phoneNumber
) {

    /**
     * The raw password must never reach logs, even accidentally.
     */
    @Override
    public String toString() {
        return "RegisterIdentityRequest[displayName=%s, email=%s, password=<redacted>, phoneNumber=%s]"
                .formatted(displayName, email, phoneNumber);
    }
}
