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

- [ ] `JWT_SECRET` is a real secret, not a default-development value, and is never logged (verified structurally — no code path logs it).
- [ ] `DB_URL`/`DB_USERNAME`/`DB_PASSWORD` point at the production database, not a local default.
- [ ] `SPRING_PROFILES_ACTIVE=prod` (or equivalent) — confirms `application-prod.yml`'s conservative logging level (`root: INFO`) and disabled Swagger UI/API docs are in effect.

## Still open (not this PR's scope — tracked for later Phase 3 work)

- [ ] Rate limiting / abuse prevention (Phase 3 PR #3).
- [ ] `/actuator/metrics` access control beyond "any authenticated user" — no admin-only gate exists yet.
- [ ] Distributed tracing, an external metrics backend, and alerting rules (explicitly out of scope for RFC-010).
- [ ] Load testing the realtime path at production-representative scale.

## Golden-path smoke test after deploy

Run the existing product flow end to end (register → author a quiz → host a session → play → results) and confirm it behaves identically to before this PR — RFC-010 changed no product behavior. The only observable differences should be in logs, metrics, and health output.
