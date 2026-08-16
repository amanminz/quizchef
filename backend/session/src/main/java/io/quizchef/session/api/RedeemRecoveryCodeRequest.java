package io.quizchef.session.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A stranded player's attempt to get back in with the code the host read
 * out. The session comes from the PIN in the path.
 */
public record RedeemRecoveryCodeRequest(
        @Schema(description = "The six digits the Quiz Master gave you", example = "482731")
        @NotBlank @Size(max = 12) String recoveryCode
) {
}
