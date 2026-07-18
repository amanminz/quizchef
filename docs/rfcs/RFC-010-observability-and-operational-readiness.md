# RFC-010 Observability and Operational Readiness

Status

Implemented

<!-- Draft | Proposed | Accepted | Implemented | Superseded by RFC-XXX
     Implemented — the whole scope shipped in one PR (Phase 3 PR #2), unlike
     RFC-004/005 which spanned several; there is no partial-Accepted phase
     to track. See README.md for the lifecycle. -->

Authors

Aman Minz

Created

2026-07-18

Updated

2026-07-18

---

# Summary

Introduces production-grade observability across the backend and a thin frontend surface: request correlation, structured logging, operational domain-event logging, in-process metrics, health/readiness/liveness, and sanitized error responses that carry a correlation id. The architecture is unchanged — this PR adds a new orthogonal `platform` module and touches configuration; it changes no product behavior.

---

# Motivation

QuizChef is feature-complete through Phase 2 and Phase 3 PR #1. Before further hardening (security, performance, deployment) the platform needs to become *operable*: every request traceable end to end, every production failure diagnosable from logs/metrics/health alone, without guessing. ARCHITECTURE.md §17/§18 already state the shape (correlation id, user id, execution time; never log secrets; a single global exception handler); this RFC is where that intent becomes real, consistently, across every module.

---

# Goals

- Every HTTP request carries a correlation id, generated or reused, echoed on the response, and present on every log line the request produces — without threading a parameter through any application or domain method signature.
- Structured (JSON) logs, consistently shaped, with no new logging framework.
- Meaningful domain transitions (identity, quiz, session, gameplay, participant) become operational log lines, distinct from audit history.
- Backend-owned, in-process metrics for HTTP, realtime transport, sessions, gameplay, and identity.
- `/actuator/health`, `/actuator/health/readiness`, `/actuator/health/liveness`, `/actuator/info`, `/actuator/metrics` — the last requiring authentication, the rest public for container orchestration.
- Every error response — including truly unexpected ones — carries a correlation id and never a stack trace.
- A new `platform` module that owns all of this, provably orthogonal to business logic.

---

# Non Goals

Distributed tracing, Prometheus exporters, Grafana dashboards, OpenTelemetry collectors, alerting rules, external monitoring vendors, audit history, analytics — all explicitly deferred to later operational phases per the driving spec. No dead configuration flags were added for any of these (e.g. no unused "tracing enabled" toggle).

---

# Background

Spring Boot 3.4.5 (already the project's BOM version) ships **native structured logging** (`logging.structured.format.console=ecs|gelf|logstash`, auto-including MDC entries as fields) and Micrometer ships transitively with `spring-boot-starter-actuator` (already an `app` dependency). Both needs from the spec are satisfiable with **zero new external dependencies** — consistent with ARCHITECTURE.md §23's "AI must never introduce new frameworks."

`websocket` already depends on `session` purely to listen to its domain events (`SessionRealtimeProjector`) — the precedent this RFC's event loggers and metrics follow directly.

---

# Proposed Design

## New module: `platform`

A ninth Gradle module, alongside `identity`/`quiz`/`session`/`websocket`/`security`, added to `backend/settings.gradle.kts`:

```text
platform/src/main/java/io/quizchef/platform/
├── correlation/   CorrelationIdFilter, RequestContextMdcInterceptor, WebMvcCorrelationConfiguration
├── logging/       RequestLoggingFilter, IdentityEventLogger, QuizEventLogger, SessionEventLogger,
│                  GameplayEventLogger, ParticipantEventLogger
└── metrics/       IdentityMetrics, SessionMetrics, GameplayMetrics
```

`platform` depends on `common`, `identity`, `quiz`, `session` (to listen to their domain events — the same shape as `websocket → session`), `spring-boot-starter-web`, `spring-boot-starter-actuator`. `app` depends on `platform`, exactly like every other module.

**Enforced, not just described**: `PlatformArchitectureTest` (in `app`, the one module where every business package and `platform` coexist on one classpath) asserts no class outside `platform`/`app` depends on `io.quizchef.platform..`. Business modules also have no compile-time dependency on `platform` at all in their `build.gradle.kts` — a second, stronger guarantee than the runtime check.

Realtime-transport-specific pieces (`RealtimeConnectionMetrics`, `RealtimeHealthIndicator`) live in `websocket` itself, not `platform` — the one module that knows STOMP exists (ADR-004), matching why `SessionRealtimeProjector` lives there.

## Request correlation

`common.correlation.CorrelationKeys` is the one place MDC key names and the `X-Correlation-Id` header name are defined — every module that needs one already depends on `common`, so nothing new ripples outward.

`CorrelationIdFilter` (`platform`, `@Order(HIGHEST_PRECEDENCE)`, ahead of Spring Security's filter at `DEFAULT_FILTER_ORDER = -100`) reuses a caller-supplied `X-Correlation-Id` or mints one; always mints a fresh `requestId` even when the correlation id is reused across a client's retry; echoes the correlation id on the response; clears MDC in a `finally` so pooled request-handling threads never leak context.

`RequestContextMdcInterceptor` (a `HandlerInterceptor`, registered by `WebMvcCorrelationConfiguration`) adds `sessionId`/`quizId`/`questionId` to MDC from Spring MVC's already-resolved path variables — an MVC-layer-only concern; no application service or domain method changed.

`JwtAuthenticationFilter` (`security`) adds `identityId` to MDC once a bearer token authenticates — two lines, using `common`'s constant, no new dependency.

**Why this is "passive and inexpensive"**: HTTP → Application → Session Engine → Realtime Events all run synchronously on one request thread in this codebase today (domain events are published in-process and synchronously; `SessionRealtimeProjector` runs on that same thread). MDC therefore propagates the whole way with zero parameter threading through any method signature, keeping domain/application code exactly as observability-free as ARCHITECTURE.md §5.7 requires.

**The one async gap**: `SessionGameplayConfiguration.gameplayTaskScheduler()` closes expired questions on its own thread pool, off any request. Its `TaskDecorator` seeds a synthetic `"timer-" + UUID` correlation id for the scheduled task's duration — the only non-trivial propagation code this RFC needed.

## Structured logging

`logging.structured.format.console: ecs` (base `application.yml`) — Elastic Common Schema, a neutral, widely-parseable JSON schema with no assumed backend, automatically including MDC fields. Per-profile log levels are unchanged (`root: INFO` in prod; `io.quizchef: DEBUG` in dev/local/test).

`RequestLoggingFilter` (`platform`, `@Order(HIGHEST_PRECEDENCE + 1)`, inside the correlation filter) logs one line per completed request: method, resolved route pattern (not the raw path — `/sessions/{id}/start` groups consistently across ids), status, duration.

**Never-log discipline**: enforced by what the event loggers choose to project, not by a filter. No event logger ever logs a password, token, or answer correctness before reveal — this is structurally easy because domain events already carry no such fields (e.g. `AnswerSubmittedEvent` has none by design).

## Domain event logging

One thin `@EventListener` component per bounded context in `platform.logging`, the same shape as `SessionRealtimeProjector`:

| Class | Events | 
|---|---|
| `IdentityEventLogger` | registered, authenticated, host-access-granted |
| `QuizEventLogger` | quiz created/published/archived, question attached/published/archived |
| `SessionEventLogger` | session created, lobby opened, started, finished |
| `GameplayEventLogger` | question opened/closed, answer revealed, leaderboard shown |
| `ParticipantEventLogger` | joined, reconnected, disconnected, answer submitted |

**Deliberately excluded**: `IdentityAuthorizedEvent` — it fires on every permission check, which is per-request noise, not an operational transition. `LeaderboardUpdatedEvent` logs only the standings count, never every participant's score — an operational event, not a replacement for the results read.

## Metrics

Backend-owned, in-process Micrometer `MeterRegistry` (no Prometheus registry). HTTP request count/latency/error rate needed no new code — `WebMvcMetricsAutoConfiguration` already instruments `http.server.requests`; this PR only exposes it.

`RealtimeConnectionMetrics` (`websocket`) listens to Spring's own STOMP session lifecycle events (`SessionConnectedEvent`/`SessionDisconnectEvent`/`SessionSubscribeEvent`/`SessionUnsubscribeEvent`), emitting `realtime.connections.opened` (counter), `realtime.connections.active` / `realtime.subscriptions.active` (gauges). A true "reconnect" is not cheaply distinguishable server-side from a first connect without client-supplied state, so this reports total opens rather than fabricating a reconnect-specific counter — an honest limitation, not a fake metric.

`SessionMetrics` and `GameplayMetrics` (`platform`) compute `session.active` (gauge), `session.completed` (counter), `session.duration` (timer), `session.participants` (distribution summary), `gameplay.answers_submitted` (counter), `gameplay.answer_latency` (timer), `gameplay.reconnect_recovery` (counter) — **all from existing domain events, with no schema change to any of them**. Duration and answer latency are computed by tracking start instants in small `ConcurrentHashMap`s keyed by session/question id, evicted the moment the corresponding "finished"/"closed" event arrives. Bounded by concurrently live sessions and questions (small at church scale), reset on restart — acceptable for v1 metrics, which are diagnostic aids, not a source of truth.

`IdentityMetrics` (`platform`) emits `identity.registrations`, `identity.logins`, `identity.host_promotions`.

No `QuizMetrics` — the driving spec's Metrics section lists no Quiz category; quiz lifecycle is covered by `QuizEventLogger`'s logs alone.

## Health, readiness, liveness

Mostly configuration. `spring-boot-starter-actuator` plus a `DataSource` already auto-configures a database health indicator; `management.endpoint.health.probes.enabled=true` (already set) already exposes `/actuator/health/readiness` and `/actuator/health/liveness`.

New: `springBoot { buildInfo { properties { name = "QuizChef" } } }` in `app/build.gradle.kts` generates `META-INF/build-info.properties`, so `/actuator/info` reports real `build.version`/`build.time` instead of a hand-written static value. `management.endpoints.web.exposure.include` grew from `health,info` to `health,info,metrics`.

`RealtimeHealthIndicator` (`websocket`) is the one genuinely new health check: it reports `AbstractBrokerMessageHandler.isBrokerAvailable()` for the qualified `simpleBrokerMessageHandler` bean — `@EnableWebSocketMessageBroker` always registers both a simple-broker and a STOMP-relay handler bean regardless of which is active, so autowiring by type alone is ambiguous; `WebSocketStompConfiguration` enables only the simple broker (church-scale, ADR-005), so the qualifier must move with that decision if a future deployment switches to a relay (RFC-008). Contributes to `/actuator/health` as `realtime`.

**Access control**: `/actuator/health`, `/actuator/health/**`, `/actuator/info` stay in `PublicEndpoints` (container orchestration needs them without a JWT). `/actuator/metrics` is deliberately left out — it falls to `.anyRequest().authenticated()`, a conservative default. Broader ops access control (IP allowlisting, an admin role) is Phase 3 PR #3's territory (security hardening), not this PR's.

## Error reporting

`ApiError` (`common`) gained a `correlationId` field, populated **inside** its existing static factories (`of`, `validation`) via `MDC.get(...)` — a one-file change with zero ripple across the ~15 existing `ApiError.of(...)` call sites. Stack traces already never reached clients (`GlobalExceptionHandler.handleUnexpected` logs full diagnostics server-side, returns only `internal.error` + a message) — unchanged.

## Frontend

Minimal, per the driving spec. `ApiClientError` gained an optional `correlationId`; the axios response interceptor passes the backend's value through; `ErrorPanel` — shared by every page's error state and by `ErrorBoundary` (the app's actual fatal-error dialog) — renders it in small print (`Reference: <id>`) only when present. Connection status and retry state already existed (`ConnectionIndicator`, `connectionStore`, `RealtimeClient`'s `reconnecting` state) and needed no new UI, only test coverage, which was missing entirely and is added here. No frontend metric collection was added, per spec.

---

# Alternatives Considered

**A dedicated JSON logging library (e.g. logstash-logback-encoder)** — rejected: Spring Boot 3.4's native structured logging does the same job with zero new dependencies, and ARCHITECTURE.md §23 forbids introducing new frameworks without cause.

**A Prometheus registry** — rejected: explicitly out of scope per the driving spec; `/actuator/metrics` (built into actuator, no exporter needed) is sufficient for backend-owned, in-process metrics at this stage.

**Threading correlation ids through application/domain method signatures** — rejected: this codebase runs every request synchronously on one thread, so MDC achieves full propagation for free; adding parameters everywhere would be the exact "observability leaking into business logic" this RFC's own principle forbids.

**Adding a domain event field for answer/session timing** — rejected in favor of transient, evictable maps inside the metrics components themselves. Changing a domain event's shape for an observability need would blur "observability owns nothing."

---

# Risks

The transient timing/participant-count maps in `SessionMetrics`/`GameplayMetrics` are process-local and reset on restart — acceptable because they back diagnostic metrics, not a source of truth, but worth remembering if a future need (e.g. accurate historical duration) arises; that would call for a persisted field, a deliberate scope change, not silently expanding these maps.

`RealtimeConnectionMetrics`'s "opened" counter conflates first-connects and reconnects — documented as a known, honest limitation rather than solved.

---

# Migration

Additive only. No existing endpoint, event, table, or wire message changed shape except `ApiError` gaining one new optional field (`correlationId`) — every existing consumer (including the frontend before this PR's own frontend changes) ignores unknown fields safely.

---

# Open Questions

None outstanding; the driving spec's Non Goals map directly to this RFC's Non Goals with no unresolved gap.

---

# Acceptance Criteria

- [x] Every HTTP request is traceable through logs via a correlation id.
- [x] Structured logging is consistent (ECS JSON, MDC fields included automatically).
- [x] Operational metrics are exposed for HTTP, realtime, sessions, gameplay, identity.
- [x] Health, readiness, and liveness endpoints exist and are public; metrics require authentication.
- [x] Every error response carries a correlation id and never a stack trace.
- [x] Production documentation includes runbooks and operational guidance (`docs/operations/`).
- [x] No gameplay or business logic changed.

---

# Future Work

Distributed tracing, a Prometheus exporter (or other metrics backend), alerting rules, and audit history remain deferred to later operational phases, as this RFC's Non Goals state.
