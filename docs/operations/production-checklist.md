# Production Checklist

What must be true before a production deploy is considered safe. Grouped by what this RFC actually changed versus what remains from earlier work.

---

## Observability (this PR)

- [ ] `logging.structured.format.console=ecs` is in effect — confirm log output is JSON, not plain text.
- [ ] `management.endpoints.web.exposure.include` includes `health,info,metrics` and nothing broader than intended (no `env`, `beans`, `heapdump`, etc. — none are opted into by this PR).
- [ ] `/actuator/health`, `/actuator/health/readiness`, `/actuator/health/liveness`, `/actuator/info` are reachable **without** authentication (container orchestration depends on this).
- [ ] `/actuator/metrics` requires authentication (confirm a request without a bearer token gets 401).
- [ ] `management.endpoint.health.show-details` is **not** `always` in the production profile (`application-prod.yml` already sets `never` — verify it wasn't accidentally loosened).
- [ ] A correlation id round-trips: a request without `X-Correlation-Id` gets one back on the response; a request that supplies one gets the same one back.

## Configuration (env-driven, already established)

- [ ] `JWT_SECRET` is a real secret, not a default-development value, and is never logged (verified structurally — no code path logs it). `JwtSecretSafetyCheck` (RFC-011) fails startup in `prod` if it matches a known placeholder — treat a boot failure here as the check working, not a bug.
- [ ] `DB_URL`/`DB_USERNAME`/`DB_PASSWORD` point at the production database, not a local default.
- [ ] `SPRING_PROFILES_ACTIVE=prod` (or equivalent) — confirms `application-prod.yml`'s conservative logging level (`root: INFO`) and disabled Swagger UI/API docs are in effect.

## Security (RFC-011)

- [ ] `CORS_ALLOWED_ORIGINS` is set to the real deployed frontend origin(s) — no default exists outside `local`/`test`, so a missing value fails startup rather than silently allowing nothing (or everything).
- [ ] `server.forward-headers-strategy: framework` is active in `prod` only — confirm the reverse proxy (Railway) actually sets `X-Forwarded-For`, or IP-based rate limits will key on the proxy's own address instead of real clients.
- [ ] Rate-limit policy (`quizchef.security.rate-limit.rules`) reviewed against actual traffic patterns before launch — the shipped defaults (RFC-011) were sized for a single Bible Quiz event, not load-tested.
- [ ] A CSP/CORS/rate-limit smoke test (see the [Runbook](runbook.md)) passes against the deployed instance, not just CI.

## Still open (not this PR's scope — tracked for later Phase 3 work)

- [ ] STOMP per-session/per-role authorization (waits on the inbound STOMP command channel, RFC-005).
- [ ] `/actuator/metrics` access control beyond "any authenticated user" — no admin-only gate exists yet.
- [ ] Distributed tracing, an external metrics backend, and alerting rules (explicitly out of scope for RFC-010).
- [ ] Distributed rate-limit storage if QuizChef ever scales horizontally (today's buckets are per-process, RFC-011).
- [ ] Load testing the realtime path at production-representative scale.

## Golden-path smoke test after deploy

Run the existing product flow end to end (register → author a quiz → host a session → play → results) and confirm it behaves identically to before this PR — neither RFC-010 nor RFC-011 changed product behavior. The only observable differences should be in logs, metrics, health output, response headers, and — only when genuinely abused — 429s.
