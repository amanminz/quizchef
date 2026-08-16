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
            // Anonymous-friendly session endpoints: guests join and resume
            // without an account, and anyone in a lobby can read its summary.
            // These are single-segment /sessions/* (join = /sessions/*/join),
            // so the host-only create/lobby/start endpoints stay authenticated.
            "/api/v1/sessions/*",
            "/api/v1/sessions/*/join",
            // Returning to a session already joined. Public for the same
            // reason join is — the audience is anonymous guests — and safe
            // because the resume token in the body is the credential: the
            // endpoint authenticates the caller itself rather than relying
            // on the filter chain to have done it.
            "/api/v1/sessions/*/participants/resume",
            // Participants (guests included) submit answers without an account;
            // per-message identity binding arrives with the STOMP command layer.
            "/api/v1/sessions/*/answers",
            // The question in play, participant-safe (no correctness until
            // revealed) — players are anonymous; the unguessable session id
            // gates it, same rationale as the summary read above.
            "/api/v1/sessions/*/questions/current",
            // One participant's own progress — points just earned and their
            // running total, never a rank (see ParticipantResultView) —
            // phase-gated server-side; same anonymous audience, with the
            // unguessable session AND participant ids gating it, the same
            // trust the answer endpoint places in the participant id. The
            // full-standings GET /results is deliberately absent: every
            // name, score, and rank there is the host's projection and
            // requires the hosting identity (live-event privacy).
            "/api/v1/sessions/*/participants/*/result",
            // One participant's own finish, released-gated server-side and
            // exact only for the reveal group. Same anonymous audience and
            // unguessable-id trust as the personal result above; this is the
            // only participant-facing source of final ranking there is.
            "/api/v1/sessions/*/participants/*/final-placement"
    };

    private PublicEndpoints() {
    }
}
