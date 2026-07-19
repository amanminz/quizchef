# Runbook

Day-2 operational procedures. Assumes the Docker Compose topology in `compose.yml` (`postgres`, `minio`, `backend`, `frontend`).

---

## Starting and stopping

```bash
docker compose up -d          # start everything
docker compose up -d backend  # start/restart just the backend (postgres/minio start as dependencies)
docker compose down           # stop everything, keep volumes
docker compose logs -f backend
```

`backend`'s own healthcheck already polls `/actuator/health`; `docker compose ps` shows `healthy`/`unhealthy` directly.

Locally, without Docker: `./gradlew :app:bootRun` (from repo root) with `DB_URL`/`DB_USERNAME`/`DB_PASSWORD`/`JWT_SECRET` set, or rely on `application-local.yml`'s defaults against a locally running Postgres.

---

## Checking health

```bash
curl -s http://localhost:8080/actuator/health | jq
curl -s http://localhost:8080/actuator/health/readiness | jq
curl -s http://localhost:8080/actuator/health/liveness | jq
curl -s http://localhost:8080/actuator/info | jq
```

`/actuator/health` (with `show-details` enabled, as it is by default in `local`/`test`) breaks down into components: `db` (Postgres reachability) and `realtime` (the STOMP broker's `isBrokerAvailable()`). A single `DOWN` component is enough to fail the aggregate.

`/actuator/metrics` requires an authenticated request (any valid bearer token — it is not yet gated by role; see the [Production Checklist](production-checklist.md)):

```bash
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/actuator/metrics | jq
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/actuator/metrics/session.active | jq
```

---

## Reading logs

Every log line is one JSON object (ECS format) on stdout. The fields that matter operationally:

```json
{"@timestamp": "...", "log.level": "INFO", "log.logger": "...", "message": "...",
 "correlationId": "...", "requestId": "...", "identityId": "...", "sessionId": "..."}
```

Filter to one request across every module it touched:

```bash
docker compose logs backend | jq -R 'fromjson? | select(.correlationId == "<id>")'
```

See the [Logging Reference](logging-reference.md) for the full field list and every operational event.

---

## Checking security hardening (RFC-011)

```bash
# Security headers on any response
curl -sI http://localhost:8080/actuator/health | grep -iE "content-security-policy|x-frame-options|referrer-policy|permissions-policy|strict-transport-security"

# CORS preflight from an allowed origin
curl -s -o /dev/null -D - -X OPTIONS http://localhost:8080/actuator/health \
  -H "Origin: http://localhost:3000" -H "Access-Control-Request-Method: GET" \
  | grep -i "access-control-allow-origin"

# Rate limiting — repeat past the login bucket's capacity (5/minute/IP by default)
for i in 1 2 3 4 5 6; do
  curl -s -o /dev/null -w "%{http_code} " -X POST http://localhost:8080/api/v1/auth/login \
    -H "Content-Type: application/json" -d '{"email":"nobody@example.com","password":"x"}'
done
# the 6th call should be 429, with Retry-After and X-RateLimit-Remaining: 0 on the response
```

`security.*` events (see the [Logging Reference](logging-reference.md)) are the fastest way to see *why* a given caller is being blocked without turning on verbose/debug logging.

---

## Common maintenance tasks

**Rotating the JWT signing secret** (`JWT_SECRET`): invalidates every outstanding token immediately (tokens are stateless but session-bound; a new secret can't validate old signatures). Every logged-in user is forced to log in again. Not the same as revoking one session — there is no per-user token revocation without a secret rotation today (RFC-002's deferred logout/refresh work).

**Database migrations**: Flyway runs automatically on backend startup (`fail-on-missing-locations: true`), against the `quizchef` schema. A failed migration fails startup — the backend never comes up in a half-migrated state. Check `flyway_schema_history` in Postgres for the applied version.

**Restarting after a bad deploy**: `docker compose up -d backend --force-recreate` after fixing the image; the healthcheck's `start_period: 40s` gives migrations and JVM warmup room before the container is judged unhealthy.

## Live-event checklist (host)

Before a projected event (RFC-004 role-scoped results, Live Event UX):

1. Open the lobby on the host laptop; check the readiness panel: `Realtime connected`, `Quiz ready`, question count, players joined.
2. `Enter Presentation Mode` (button, host lobby/live screens) — chrome hides and the browser requests fullscreen; if the browser refuses, the layout still applies and a hint suggests the browser's own fullscreen shortcut. A screen wake lock keeps the projector awake where supported.
3. Share the code via `Copy code` or `Copy Join Details` (quiz title, code, and the participant URL).
4. Join at least two phones; confirm names appear on the participant wall and the count updates live. Participants pick English or हिन्दी at join — each phone receives its language (default-language fallback when a translation is missing), and every participant screen names the quiz.
5. `Start session` asks for confirmation and shows the late-join setting — read it before confirming.
6. During play: participants see only their own rank (never the leaderboard); the host screen shows full standings, both languages of the question when authored, and the live `answered / eligible` counter. When everyone has answered, `Close Question` pulses — it's an invitation, not an automation; the timer still closes the question if you wait. If the realtime banner appears, gameplay is unaffected; wait for `Connection restored.`
7. At the finish, the podium reveals 3rd → 2nd → 1st (Skip available; Replay is display-only). A refresh renders the completed results from the backend — it never replays commands.
