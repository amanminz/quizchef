# RFC-011 Security Hardening and Abuse Prevention

Status

Implemented

<!-- Draft | Proposed | Accepted | Implemented | Superseded by RFC-XXX
     Implemented — the whole scope shipped in one PR (Phase 3 PR #3), the
     same precedent as RFC-010. See README.md for the lifecycle. -->

Authors

Aman Minz

Created

2026-07-18

Updated

2026-07-18

---

# Summary

Strengthens QuizChef's security posture — HTTP security headers, CORS, rate limiting, abuse prevention, input validation, secrets/configuration, and dependency review — without changing product behavior. Extends Phase 3 PR #2's operational event logging with security-specific events. The architecture is unchanged: this PR reduces risk, adds no feature.

---

# Motivation

Phase 2 shipped the whole product; Phase 3 PR #1 added durable roles; PR #2 made the system observable. Neither reviewed the externally reachable surface for hardening: no CORS configuration existed at all, no explicit security headers, zero authorization on STOMP frames, zero rate limiting anywhere, and several input-validation gaps (unbounded collections, no request body size cap). This RFC is where that review becomes concrete, backed by ground-truth findings, not assumptions.

---

# Goals

- Explicit, intentional HTTP security headers (CSP, frame options, referrer policy, permissions policy, HSTS) instead of bare framework defaults.
- A real CORS allowlist — the actual production topology (Cloudflare Pages frontend + Railway backend, per ARCHITECTURE.md) is cross-origin, so this closes a functional gap, not just a hardening one.
- Rate limiting for every abuse-prone operation, identity-aware where possible, IP-based for anonymous traffic, with standard rate-limit response headers.
- STOMP destination well-formedness validation.
- Closed input-validation gaps: unbounded collections/strings, a request body size cap.
- A fail-fast check against deploying to production with a known development JWT secret.
- Security-specific operational event logging, extending PR #2's pattern.
- A documented threat model and accepted-risk register.

---

# Non Goals

OAuth, MFA, CAPTCHA, enterprise SSO, an audit trail, encryption-at-rest changes, key management, penetration testing, an external WAF. All explicitly out of scope per the driving spec — later operational phases, if ever.

---

# Background

Three research passes against the actual codebase (not assumptions) preceded this design:

- **No CORS configuration existed at all** — zero matches for `CorsConfiguration`/`@CrossOrigin`/`CorsConfigurationSource` anywhere in `backend/`.
- **No explicit security headers** — `SecurityConfiguration` never called `.headers(...)`; whatever protection existed was Spring Security 6's bare defaults.
- **Zero STOMP authorization** — `/ws/**` fully public per `PublicEndpoints`, with a comment already flagging this as deferred to when inbound STOMP commands land (RFC-005's own stated gap, still open).
- **JWT configuration was already solid**: HS256, a ≥32-byte secret enforced in `JwtProperties`' compact constructor, TTL validated. No logout endpoint exists (confirmed absent — building one would be a feature, not hardening).
- **Zero rate limiting anywhere.**
- **Real, mechanical validation gaps**: several `List`/`Set` fields with no upper bound, language-code strings with no length cap, `BibleReferenceDto.verseEnd` unvalidated with no cross-field check against `verseStart`, `ReconnectRequest` with no structural XOR check between its two identifiers, and no request body size limit configured anywhere.
- **Dependencies were clean**: `npm audit` reported zero vulnerabilities; backend pinned versions showed nothing actionable. No Dependabot config existed.
- **Frontend**: 401 and 403 already handled reasonably (session expiry; `UnauthorizedState`/`PermissionBanner` reacting to `ApiClientError`). 429 had zero handling anywhere.

---

# Proposed Design

## Package layout — where the user's suggested structure was adapted

The user recommended `platform/security/{headers,ratelimit,validation,authorization,hardening,configuration}/`, mirroring how observability was organized in PR #2. This RFC follows it where new cross-cutting *infrastructure* is genuinely needed, and deliberately does not create empty packages where the right fix is a targeted edit to already-well-placed code:

| Suggested package | Outcome | Why |
|---|---|---|
| `headers/` | Not created — added directly to `security` module's existing `SecurityConfiguration` | That's already where the `HttpSecurity` chain is built; relocating a working, tested filter chain into `platform` would be pure churn |
| `ratelimit/` | Created: `platform.security.ratelimit` | Real new cross-cutting infrastructure |
| `hardening/` | Created: `platform.security.hardening` | `MaxRequestSizeFilter` |
| `configuration/` | Created: `platform.security.configuration` | `JwtSecretSafetyCheck` |
| `validation/` | Not created | The fixes are per-field annotation additions to DTOs that already own them (identity/quiz/session) — no shared validation infrastructure to centralize |
| `authorization/` | Not created | Already centralized in `identity.application.AuthorizationService`/`RolePermissions`; the audit found no bug to fix, so there is nothing to put in a new package |

STOMP hardening stays in `websocket`, not `platform` — ADR-004's boundary (only websocket knows STOMP exists), the same reasoning that placed `RealtimeConnectionMetrics`/`RealtimeHealthIndicator` there in PR #2.

## 1. HTTP Security Headers (`security` module)

`SecurityConfiguration` gained an explicit `.headers(...)` block:
- **Content-Security-Policy**: `default-src 'none'; frame-ancestors 'none'; base-uri 'none'` — safe for a pure JSON API. Added as a raw `HeaderWriter` (not the `.contentSecurityPolicy(...)` DSL, which has no request-matcher support) wrapped in a `DelegatingRequestMatcherHeaderWriter` scoped away from `/swagger-ui/**`/`/v3/api-docs/**` (reachable only outside `prod`, where `springdoc` is disabled entirely), so the API's strict policy never breaks the docs UI.
- **X-Frame-Options: DENY**, explicit (matches the prior implicit default, now documented rather than assumed).
- **Referrer-Policy: strict-origin-when-cross-origin**, explicit.
- **Permissions-Policy**: `camera=(), microphone=(), geolocation=(), payment=()` — via Spring Security 6.4's `.permissionsPolicyHeader(...)` DSL (the non-deprecated variant; `.permissionsPolicy(...)` is deprecated-for-removal and, confusingly, does not return the parent builder for chaining).
- **Strict-Transport-Security**: `includeSubDomains(true)`, one-year max-age. Spring Security's own `HstsHeaderWriter` only sends this header over HTTPS (checks `request.isSecure()`), so it is safe to enable unconditionally rather than profile-gating it.

## 2. CORS (`security` module)

A `CorsConfigurationSource` bean, wired via `.cors(...)` (previously entirely absent from the filter chain). Allowed origins come from `quizchef.security.cors.allowed-origins` (`CorsProperties`, a `@ConfigurationProperties` record mirroring `JwtProperties`' own style — a compact-constructor `IllegalArgumentException` on an empty list, not Bean Validation, for consistency with the existing properties class in this codebase). `local`/`test` default to `http://localhost:3000`; `dev`/`prod` require it set externally, exactly mirroring how `JWT_SECRET` is already handled — no insecure fallback. Allowed methods are the ones the API actually uses; allowed/exposed headers include `Authorization`, `Content-Type`, and `X-Correlation-Id` (cross-referencing PR #2). `allowCredentials(false)` — auth is Bearer-token based, never a cookie, so no credentialed cross-origin request is ever needed.

## 3. STOMP Destination Validation (`websocket` module)

`StompDestinationValidationInterceptor` (a `ChannelInterceptor`, registered via `WebSocketStompConfiguration.configureClientInboundChannel`): on SUBSCRIBE, validates the destination matches one of `Topics`' known patterns with a well-formed UUID suffix; on SEND, validates it at least carries the configured `/app` prefix. Anything else is rejected (a STOMP ERROR frame back to the client) before it reaches the broker — the concrete answer to "malformed WebSocket frames" in the abuse-prevention list. Logs `security.invalid_stomp_destination` (WARN) inline.

**Deliberately not built**: real per-session/per-role authorization (can this subscriber actually see *this* session's host channel?) requires knowing who is connecting, which requires the inbound STOMP command channel RFC-005 already defers — nothing resolves a Principal on CONNECT today, and wiring one up with no consumer would be exactly the "placeholder implementation" ARCHITECTURE.md forbids. This is an **accepted risk**, tied to the same already-on-record RFC-005 gap, not a new one.

## 4. Rate Limiting (`platform.security.ratelimit`)

- **`TokenBucket`** — a small hand-rolled bucket (capacity, refill window, `Clock`-driven for testability), no new dependency (no Bucket4j) — appropriate for a single-instance, church-scale deployment (RFC-008).
- **`RateLimitProperties`** (`@ConfigurationProperties(prefix = "quizchef.security.rate-limit")`) — a `defaultRule` (60/minute) applied to any route without its own entry, plus a `rules` map keyed by exactly `"METHOD route-pattern"` (the same resolved Spring MVC pattern `RequestContextMdcInterceptor` already reads from PR #2), so no endpoint is ever completely unbounded. **Gotcha, caught by `RateLimitingIntegrationTest` before it shipped**: YAML map keys containing spaces or slashes are silently mangled by Spring Boot's relaxed binding unless wrapped in bracket notation (`"[POST /api/v1/auth/login]"`) — plain quoted keys strip the special characters entirely, silently falling back to the default rule. Configured policies: login 5/min/IP, register 3/min/IP, create-session 10/min/identity, join-session 10/min/IP, reconnect 10/min/IP, answer-submission 20/10s/identity-or-IP, publish-quiz 10/min/identity, host-access request 3/min/identity.
- **`ClientIpResolver`** — deliberately just `request.getRemoteAddr()`. Trusting a client-supplied `X-Forwarded-For` directly would make IP-based limits trivially spoofable; that translation happens once, upstream, via Spring Boot's own `ForwardedHeaderFilter` (`server.forward-headers-strategy: framework`), enabled **only in `prod`**, where Railway's reverse proxy is trusted to set the header correctly — outside `prod` there is no such proxy, so trusting it would be spoofable for no benefit. This closes the long-standing "X-Forwarded-For" item from the reviewer-conventions memory.
- **`RateLimitingInterceptor`** — a `HandlerInterceptor`, not a `Filter`: the bucket key depends on the resolved route *pattern*, which (like PR #2's path-variable extraction) is only available once Spring MVC's handler mapping has run, i.e. inside `preHandle` — the correct, already-established mechanism to reject before the controller executes. Keys by identity id when present in MDC (set by `JwtAuthenticationFilter`, which runs inside Spring Security's chain, already completed by the time any `HandlerInterceptor` runs), else the resolved client IP. Sets `X-RateLimit-Limit`/`-Remaining`/`-Reset` on **every** response, allowed or blocked, plus `Retry-After` and a `rate-limit.exceeded` `ApiError` body on the 429 case. The three `X-RateLimit-*` headers are not consumed by the frontend today — added because they are cheap here and genuinely useful for future CLI clients, integrations, and debugging.
- **Disabled by default in the `test` profile.** Integration tests share one cached Spring context — and this bean's in-memory buckets — across many test methods and even across test classes; a tight bucket exhausted by an early test would spuriously fail an unrelated later one. `RateLimitingIntegrationTest` re-enables it via a `@SpringBootTest(properties = ...)` override, which gives it its own isolated context, to prove the real 429 path works end to end.

## 5. Abuse Prevention

- **Oversized payloads**: `MaxRequestSizeFilter` (`platform.security.hardening`, a true early `Filter` — Content-Length checking needs no route resolution) rejects any request over 256 KB with 413, before any body parsing.
- **Duplicate answer submissions**: already fully handled by existing domain logic (`session.answer.not-accepted` 409, already treated as confirmation not failure per Phase 2 PR #4) — no new code, documented as an existing protection.
- **Invalid PIN brute force / reconnect storms**: covered by the join/reconnect rate-limit buckets — no separate lockout mechanism.
- **Malformed WebSocket frames**: covered by §3.

## 6. Input Validation

Fixed in place, using patterns already idiomatic to this codebase's `jakarta.validation` usage:
- Every previously-unbounded `List`/`Set` gained a `@Size(max = N)` (tags capped via container-element constraints — `List<@NotBlank @Size(max = 30) String> tags` — a plain jakarta.validation 2.0+ feature, no custom framework).
- Language-code fields gained `@Size(max = 35)` (BCP-47's realistic ceiling) at the DTO boundary; `LanguageCode` still owns actual format validation deeper in the stack.
- `LoginRequest.password` gained `@Size(max = 128)`, matching `RegisterIdentityRequest`'s own existing cap — closes a CPU-cost-via-huge-password-before-hashing gap without touching the deliberate absence of a password-*policy* constraint on login (the existing code comment about not leaking policy stays true; this is a ceiling, not a floor).
- `BibleReferenceDto` and `ReconnectRequest` each gained a cross-field check via a record-compatible `@AssertTrue`-annotated boolean accessor (`isVerseRangeValid()`, `isExactlyOneIdentifierPresent()`) — Bean Validation's standard mechanism for JavaBean-style boolean properties, which records support natively since the compiler-generated accessors are irrelevant to how Hibernate Validator discovers `isXxx()` methods. No custom constraint annotation needed.
- `displayOrder`-shaped integers gained a `@Max` alongside their existing `@Min(1)`.
- A Jackson `StreamReadConstraints` customizer (`common.configuration.JacksonConfiguration`, alongside the existing Jackson/OpenAPI configuration) caps the maximum JSON string length explicitly — belt-and-suspenders alongside `MaxRequestSizeFilter`, which already bounds the whole request body well below Jackson's own 20 MB default.

**Accepted risk, not fixed**: `MediaReferenceDto.storageKey`'s format (e.g. path-traversal-shaped values) is not further constrained beyond length. The media module is still an empty scaffold (RFC-007 unbuilt) — nothing consumes this key against object storage yet, so validating its format now would be guessing at a contract RFC-007 hasn't designed.

## 7. Secrets & Configuration

`JwtSecretSafetyCheck` (`platform.security.configuration`, `@Profile("prod")`, `@PostConstruct`): fails application startup if the configured `JWT_SECRET` matches either checked-in placeholder (`quizchef-local-development-secret-0001`, `quizchef-test-only-signing-secret-0001`) — fail-fast against an accidental prod deploy with a dev secret. Actuator exposure and logging-of-secrets discipline were already correct from PR #2 (metrics require auth, health/info public, nothing else exposed) — reviewed, no change needed. `.github/dependabot.yml` added (gradle `/backend`, npm `/frontend`, weekly) as the ongoing mechanism, since the manual dependency review found nothing currently actionable.

## 8. Security Event Logging

Extends PR #2's operational event logging with security-specific events — "why is this user getting blocked," "are we seeing join-code brute-forcing," never audit/SIEM. A consistent `security.<event>` naming convention, but each event is logged where the detecting code already lives, not centralized through an event-listener indirection unless that plumbing already exists in the right direction:

| Event | Level | Where | Mechanism |
|---|---|---|---|
| `security.rate_limit_triggered` | WARN | `platform.security.ratelimit.RateLimitingInterceptor` | Direct log |
| `security.oversized_request` | WARN | `platform.security.hardening.MaxRequestSizeFilter` | Direct log |
| `security.jwt_secret_validation_failed` | ERROR | `platform.security.configuration.JwtSecretSafetyCheck` | Direct log |
| `security.invalid_jwt` | WARN | `security` module's `JwtAuthenticationFilter` | Direct log — no new `platform` dependency needed |
| `security.invalid_stomp_destination` | WARN | `websocket`'s `StompDestinationValidationInterceptor` | Direct log — same reasoning, ADR-004 |
| `security.authorization_denied` | INFO | `identity.application.AuthorizationService` | **Routed through platform**: `AuthorizationService` already published `IdentityAuthorizedEvent` on the success path via its existing `DomainEventPublisher`; it gained a sibling `IdentityAuthorizationDeniedEvent` on denial, and PR #2's `IdentityEventLogger` (`platform`) gained one more `@EventListener` for it — the one case where the identity→platform event-listening plumbing already existed and fit perfectly. `AuthorizationService`'s own prior direct log line was removed to avoid double-logging the same occurrence through two mechanisms. |

Not published for anonymous callers (401, before any permission check even runs — there is no identity to report). `docs/operations/logging-reference.md` gained a matching table.

---

# Alternatives Considered

**Bucket4j (or another rate-limiting library)** — rejected: a hand-rolled token bucket is a few dozen lines, needs no new dependency, and is entirely sufficient for a single-instance deployment; matches PR #2's "no new frameworks" outcome.

**A `Filter` for rate limiting** — rejected in favor of a `HandlerInterceptor`: the bucket key depends on the resolved route pattern, which is only available after Spring MVC's handler mapping runs (inside the dispatch a `Filter` merely wraps), so a `HandlerInterceptor.preHandle` is the mechanically correct place to reject before the controller executes.

**Trusting `X-Forwarded-For` unconditionally** — rejected: spoofable without a trusted reverse proxy in front. Spring Boot's own `ForwardedHeaderFilter`, enabled only where such a proxy exists (`prod`), is the correct, already-built mechanism.

**Building real per-session STOMP authorization now** — rejected: requires an inbound command channel and Principal resolution that don't exist yet (RFC-005's own deferred scope); building it with no consumer would be a placeholder implementation, not a defensible security improvement.

---

# Risks

`RateLimitingInterceptor`'s in-memory buckets are per-process and reset on restart — acceptable for a single-instance deployment, but would need a shared store (Redis or similar) if QuizChef ever scales horizontally (RFC-008's territory, not this RFC's).

STOMP per-session authorization remains genuinely absent — an attacker who guesses or learns a session id can still subscribe to its topics. Mitigated today only by the id being effectively unguessable and by ADR-006's own gating discipline (correctness/scores never cross the wire before their moment regardless of who's listening) — not by this RFC's destination-format check, which only rejects malformed frames.

`MediaReferenceDto.storageKey` format validation remains deferred to RFC-007.

---

# Migration

Additive only, except where explicitly noted: `ReconnectRequest` and `BibleReferenceDto` now reject previously-accepted malformed shapes (both/neither reconnect identifier; `verseEnd < verseStart`) — verified against every existing test fixture in the repository, none of which exercised those shapes. No existing endpoint's success-path contract changed.

---

# Open Questions

None outstanding.

---

# Acceptance Criteria

- [x] Public endpoints reviewed (headers, CORS, STOMP).
- [x] Authorization audited — no bug found, and its denial path now logs a real operational event.
- [x] Abuse-prone operations rate limited, with standard response headers.
- [x] Security headers configured explicitly.
- [x] Input validation comprehensive against the audit's findings.
- [x] Sensitive configuration audited (JWT secret fail-fast, actuator exposure re-confirmed).
- [x] Security documentation exists (this RFC + operational docs).
- [x] No product functionality changed.

---

# Future Work

STOMP per-session/per-role authorization, once the inbound command channel lands (RFC-005). `MediaReferenceDto.storageKey` format validation, once RFC-007 designs the media contract. Distributed rate-limit storage, if QuizChef ever scales horizontally (RFC-008). OAuth/MFA/CAPTCHA/SSO/audit trail — all explicitly out of scope here, unassigned to any future RFC.
