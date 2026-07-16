package io.quizchef.session.api;

import io.quizchef.session.application.JoinSessionCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Joins a session by PIN. Sent by guests and registered players alike; a
 * registered player's identity comes from their bearer token, a guest's from
 * nothing (a token is issued back).
 */
public record JoinSessionRequest(
        @Schema(example = "Aman", description = "The per-session nickname; need not be unique")
        @NotBlank @Size(max = 100) String displayName,
        @Schema(example = "en", description = "BCP-47 tag the participant wants to play in")
        @NotBlank String preferredLanguage
) {

    JoinSessionCommand toCommand(String sessionPin) {
        return new JoinSessionCommand(sessionPin, displayName, preferredLanguage);
    }
}
