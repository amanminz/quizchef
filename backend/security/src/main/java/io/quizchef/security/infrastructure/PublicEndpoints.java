package io.quizchef.security.infrastructure;

/**
 * Central registry of endpoint patterns reachable without authentication.
 *
 * <p>Modules that introduce public endpoints (for example {@code /api/v1/auth/**})
 * add their patterns here so the whitelist never scatters across configurations.
 */
public final class PublicEndpoints {

    public static final String[] ALL = {
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            // The STOMP/SockJS handshake. The connection opens publicly;
            // per-message authorization (who may subscribe to which session
            // topic, who may send commands) arrives with Session APIs.
            "/ws/**"
    };

    private PublicEndpoints() {
    }
}
