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

## Common maintenance tasks

**Rotating the JWT signing secret** (`JWT_SECRET`): invalidates every outstanding token immediately (tokens are stateless but session-bound; a new secret can't validate old signatures). Every logged-in user is forced to log in again. Not the same as revoking one session — there is no per-user token revocation without a secret rotation today (RFC-002's deferred logout/refresh work).

**Database migrations**: Flyway runs automatically on backend startup (`fail-on-missing-locations: true`), against the `quizchef` schema. A failed migration fails startup — the backend never comes up in a half-migrated state. Check `flyway_schema_history` in Postgres for the applied version.

**Restarting after a bad deploy**: `docker compose up -d backend --force-recreate` after fixing the image; the healthcheck's `start_period: 40s` gives migrations and JVM warmup room before the container is judged unhealthy.
