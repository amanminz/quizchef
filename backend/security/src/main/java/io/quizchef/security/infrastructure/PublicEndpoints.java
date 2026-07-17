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
            "/ws/**",
            // Anonymous-friendly session endpoints: guests join and reconnect
            // without an account, and anyone in a lobby can read its summary.
            // These are single-segment /sessions/* (join = /sessions/*/join),
            // so the host-only create/lobby/start endpoints stay authenticated.
            "/api/v1/sessions/*",
            "/api/v1/sessions/*/join",
            // Participants (guests included) submit answers without an account;
            // per-message identity binding arrives with the STOMP command layer.
            "/api/v1/sessions/*/answers"
    };

    private PublicEndpoints() {
    }
}
