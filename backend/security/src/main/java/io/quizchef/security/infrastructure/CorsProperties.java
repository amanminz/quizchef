package io.quizchef.security.infrastructure;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The cross-origin allowlist. No profile-independent default exists — a
 * deployment must say explicitly which origins may call it, the same
 * discipline {@code JwtProperties} already applies to the signing secret.
 *
 * @param allowedOrigins origins permitted to call the API cross-origin
 *                       (e.g. the deployed frontend's own origin)
 */
@ConfigurationProperties(prefix = "quizchef.security.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        if (allowedOrigins == null || allowedOrigins.isEmpty()) {
            throw new IllegalArgumentException("quizchef.security.cors.allowed-origins must not be empty");
        }
        allowedOrigins = List.copyOf(allowedOrigins);
    }
}
