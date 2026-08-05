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
- [ ] `server.forward-headers-strategy: native` is active in `prod` only — confirm the reverse proxy actually sets `X-Forwarded-For`, or IP-based rate limits will key on the proxy's own address instead of real clients.
- [ ] **The proxy chain is trusted, and only the proxy chain.** `native` (Tomcat's `RemoteIpValve`) takes the address a trusted hop observed — the rightmost entry past `internal-proxies` — rather than the leftmost, which is whatever the client chose to send. The previous `framework` strategy read the leftmost value, which made every IP-keyed limit both evadable (edit your own prefix for a fresh bucket) and weaponizable (name someone else's address to spend their budget), authentication and registration included. Verify after deploy, from two different networks:

  ```bash
  # Six times from network A — the sixth should be 429
  curl -s -o /dev/null -w "%{http_code} " -X POST https://<api-host>/api/v1/auth/login \
    -H 'Content-Type: application/json' -H 'X-Forwarded-For: 203.0.113.10' \
    -d '{"email":"nobody@example.com","password":"x"}'
  ```

  Then once more from network A with a *different* injected `X-Forwarded-For` — it must **still** be 429. Then once from network B — it must **not** be. If the injected header changes the outcome, the platform is passing the client's value through as the rightmost entry and `TRUSTED_PROXY_REGEX` needs narrowing to the real proxy addresses.
- [ ] Rate-limit policy (`quizchef.security.rate-limit.rules`) reviewed against actual traffic patterns before launch.
- [ ] **Venue capacity confirmed against the room you are about to run in.** The participant-facing routes are limited per client IP, and at a venue every phone shares one NAT address — so these are per-venue budgets. Defaults support a room of **150**:

  | Route | Capacity | Window | Override |
  | --- | --- | --- | --- |
  | `POST /sessions/{pin}/join` | 150 | 1 min | `PARTICIPANT_RATE_LIMIT_CAPACITY` |
  | `POST /sessions/reconnect` | 300 | 1 min | `PARTICIPANT_RECONNECT_RATE_LIMIT_CAPACITY` |
  | `POST /sessions/{id}/answers` | 200 | 10 s | `PARTICIPANT_ANSWER_RATE_LIMIT_CAPACITY` |

  Reconnect is double the room size because every device reconnects on join, on refresh, and after any dropped websocket — a wifi blip reconnects everyone at once. Answers are sized for a whole room replying within seconds of a question opening.

  For a larger room, raise all three together. A 300-person event wants at least:

  ```
  PARTICIPANT_RATE_LIMIT_CAPACITY=350
  PARTICIPANT_RECONNECT_RATE_LIMIT_CAPACITY=700
  PARTICIPANT_ANSWER_RATE_LIMIT_CAPACITY=400
  ```

  The answer capacity must be **at least the expected attendance** — nearly everyone answers within the same few seconds, so a budget below the headcount silently drops answers.

- [ ] **Capacity is not below the session's own `maxParticipants`.** They are different limits and do not move together: `maxParticipants` defaults to **500** per session, while the participant capacity above defaults to 150 *per minute per address*. A 500-person session is therefore admissible in principle but would take roughly four minutes to fill from one venue, and answers would be dropped throughout. If a session is configured to hold more people than the venue capacity allows, raise the capacity to match before the event.

  Measured symptom of setting them too low: participants see the join screen fail, or their answers silently not register.

  Authentication and registration limits are deliberately **not** sized this way — they are per person, not per room, and stay small (login 5/min, register 3/min, host-access 3/min).
- [ ] A CSP/CORS/rate-limit smoke test (see the [Runbook](runbook.md)) passes against the deployed instance, not just CI.

## Still open (not this PR's scope — tracked for later Phase 3 work)

- [ ] STOMP per-session/per-role authorization (waits on the inbound STOMP command channel, RFC-005).
- [ ] `/actuator/metrics` access control beyond "any authenticated user" — no admin-only gate exists yet.
- [ ] Distributed tracing, an external metrics backend, and alerting rules (explicitly out of scope for RFC-010).
- [ ] Distributed rate-limit storage if QuizChef ever scales horizontally (today's buckets are per-process, RFC-011).
- [ ] Load testing the realtime path at production-representative scale.

## Golden-path smoke test after deploy

Run the existing product flow end to end (register → author a quiz → host a session → play → results) and confirm it behaves identically to before this PR — neither RFC-010 nor RFC-011 changed product behavior. The only observable differences should be in logs, metrics, health output, response headers, and — only when genuinely abused — 429s.
