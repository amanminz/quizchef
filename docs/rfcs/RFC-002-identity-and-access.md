# RFC-002 Identity and Access

Status

Implemented

Authors

Aman Minz

Created

2026-07-14

Updated

2026-07-16

---

# Summary

Defines the identity bounded context: durable identities for registered users and guests, separated credentials and profiles, durable login sessions, roles, the framework-independent request context, and the JWT and password-hashing infrastructure. The foundation is implemented first without any public API; registration, login, and the authentication filter build on it in subsequent PRs.

---

# Motivation

Every future capability — registration, login, guest join, participants, session recovery — needs a stable answer to "who is acting?". ADR-002 established Identity → User → Participant; this RFC turns the Identity layer into a concrete domain and infrastructure while keeping business code free of framework coupling.

---

# Goals

- One durable Identity per actor, registered or guest.
- Secrets, contact details, and transport data kept out of the Identity aggregate.
- Business services that never touch Spring Security.
- Token and password infrastructure ready for the registration and login PRs.

---

# Non Goals

- Logout and refresh APIs — later PRs.
- Authorization rules and role management — PR #4.
- Role assignment and administration.
- Password reset and email verification (out of scope for v1).

---

# Background

The repository bootstrap created an `auth` Gradle module as empty scaffolding. The domain documentation (ADR-002) speaks of identities, not authentication — authentication is just one thing an identity can do. The bounded context is therefore named `identity`.

---

# Proposed Design

## Bounded context

The `auth` module was renamed to `identity` (it contained no code, so no compatibility module is needed). The `identity` module owns the identity domain; the `security` module owns Spring Security integration and adapts it to the identity domain's ports.

## Aggregates

Four aggregates, each referencing the identity by id only — no object graphs across aggregate boundaries:

- **Identity** — who someone is: `identityType` (REGISTERED | GUEST), `status` (ACTIVE | DISABLED). Never contains passwords, email, phone, tokens, or transport information.
- **Credentials** — the password hash of a registered identity, one per identity. Plain passwords never exist at rest; hashing happens behind the domain port `PasswordHasher`.
- **UserProfile** — display name, unique email (normalized to trimmed lower case; the login identifier), optional phone number (reserved for future login methods), nullable avatar URL.
- **IdentitySession** — a durable login session: nullable refresh-token hash and device fingerprint, nullable user agent and IP address (not every client reveals them), `lastSeenAt`, `lastAuthenticatedAt`, `revoked`. `lastSeenAt` moves on every interaction; `lastAuthenticatedAt` only when the identity proves itself (login, token refresh) — different questions, different timestamps. Distinct from quiz sessions (RFC-004): an IdentitySession is "logged in", a Participant is "playing". Revoking clears the refresh-token hash.

Every entity carries a domain-assigned UUID id plus `createdAt`/`updatedAt` (coding standard), so `Credentials` and `UserProfile` use a unique `identity_id` column rather than identity-as-primary-key.

## IdentityReference

Other modules never hold the identity aggregates. They hold an **IdentityReference** — a value object carrying only `identityId` and `identityType`: who is acting, nothing more. A future Participant references an identity this way without ever seeing credentials, profiles, or sessions. `Identity.reference()` produces it.

## Designed-in extension points (not implemented)

- `IdentityStatus` will grow (`LOCKED`, `PENDING_VERIFICATION`). All status checks go through `isActive()` (`status == ACTIVE`), so any future state fails safe, and adding one is a single enum constant plus a one-line CHECK-constraint migration — no redesign.
- `IdentityType` reserves **SYSTEM** for non-human actors (scheduled jobs, AI users, migrations, imports). Not added until a system actor exists.

## Roles

`Role` enum: ADMIN, QUIZ_MASTER, USER. Guests hold no roles. Roles are not persisted yet — assignment logic is a later concern, and an empty join table would be speculative.

## Request context

`CurrentUser` (identityId, authenticated, identityType, roles) with the `CurrentUserProvider` port lives in the identity domain. Business services depend only on these. The security module provides the only adapter that reads `SecurityContextHolder`: it maps a verified `IdentityPrincipal` to `CurrentUser` and anything else — including Spring's anonymous authentication — to `CurrentUser.anonymous()`. The future JWT filter will place `IdentityPrincipal` into the context; nothing else changes.

## Password hashing: why Argon2

`Argon2PasswordEncoder` (Argon2id, Spring Security 5.8+ defaults: 16 MiB memory, 2 iterations, parallelism 1) implements the `PasswordHasher` port. Argon2 over BCrypt because:

- Argon2 is memory-hard: GPU/ASIC cracking rigs are bounded by memory bandwidth, not just compute, while BCrypt's 4 KB working set fits in on-chip caches.
- It won the Password Hashing Competition (2015) and is OWASP's first-choice recommendation.
- Parameters (memory, iterations, parallelism) can scale with future hardware; BCrypt only has a single cost factor.

The identity module depends only on `spring-security-crypto` (plus BouncyCastle, which provides the Argon2 primitives) — deliberately not `spring-boot-starter-security`, so business code cannot acquire a Spring Security dependency by accident.

## JWT infrastructure

JJWT (`jjwt-api`/`impl`/`jackson`) issues and verifies HMAC-SHA256 tokens. Claims: `iss` (asserted on validation), `sub` = identityId, `iat`, `exp`, `identityType`, `roles`, and optionally `aud` — when `JWT_AUDIENCE` is configured it is stamped on issued tokens and asserted on validation; when absent, tokens carry no audience. Configuration comes from `quizchef.security.jwt.*`: the secret arrives via `JWT_SECRET` (minimum 256 bits, enforced at startup), the access-token lifetime via `JWT_ACCESS_TOKEN_TTL` (default 15 minutes). Expired and malformed tokens map to distinct error codes (`identity.token.expired` / `identity.token.invalid`) so clients know when to refresh. Time comes from the shared `Clock` bean, so expiry is testable. JJWT was chosen over Nimbus (`spring-security-oauth2-jose`) because the latter would pull Spring Security into the identity module.

## Error model

Shared exception categories in `common` (`ResourceNotFound`, `Conflict`, `Unauthorized`, `Forbidden` extending `QuizChefException` with stable error codes), identity-specific exceptions (`IdentityNotFoundException`, `DuplicateEmailException`, `InvalidCredentialsException`) extending them. A single global `@RestControllerAdvice` maps categories to HTTP statuses and renders the shared `ApiError` format, including field errors from Jakarta validation — the reusable validation infrastructure for future request DTOs. Messages never disclose whether the email or the password was wrong, and never echo email addresses.

## Persistence

Flyway `V2__identity.sql` creates `identities`, `credentials`, `user_profiles`, `identity_sessions` in the `quizchef` schema with `IF NOT EXISTS` guards, CHECK constraints on the enum columns, unique constraints on `credentials.identity_id`, `user_profiles.identity_id`, and `user_profiles.email`, and an index on `identity_sessions.identity_id`.

Flyway is now pinned to `default-schema: quizchef` / `schemas: quizchef`. Without the pin, Flyway resolves its default schema from the connection: `public` on a fresh database (before `quizchef` exists) but `quizchef` on the next start — where it finds a non-empty schema without a history table and aborts. Pinning makes the history location deterministic; Flyway creates the schema itself, and `V1__init.sql` remains as documentation and as the guard for databases created outside Flyway. Local databases created before this pin need one `docker compose down -v`.

---

# Alternatives Considered

**Keep `auth` as a compatibility module** — rejected: it contained no code; a delegating shell would be pure ceremony.

**BCrypt** — rejected in favor of Argon2id (see above).

**Nimbus JOSE via spring-security-oauth2-jose** — rejected: drags Spring Security into the identity module.

**identityId as primary key for Credentials/UserProfile** — rejected: coding standards require id + createdAt + updatedAt on every entity; a unique `identity_id` column provides the same 1:1 guarantee.

**Persisting roles now** — rejected: no assignment logic exists yet; an empty table is speculative schema.

---

# Risks

- The local/test JWT secrets in profile files are development-only defaults; `dev` and `prod` refuse to start without `JWT_SECRET`. They must never be reused in shared environments.
- Argon2's 16 MiB per hash is deliberate work: a burst of hashing (mass logins) costs real memory. Rate limiting is already planned (architecture, "future").
- `IdentitySession` rows accumulate; a cleanup policy (expiry of revoked/stale sessions) is deferred to the login PR where session lifetimes are defined.

---

# Migration

`V2__identity.sql` is additive; no existing data is affected.

---

# Open Questions

- Refresh-token rotation policy and session lifetime (decided in the login PR).
- Guest identity retention: when are stale guest identities purged?
- Where role assignment lives (admin API vs. seed data) once needed.

---

# Acceptance Criteria

- [x] Identity, Credentials, UserProfile, IdentitySession aggregates with behavior and tests.
- [x] `V2__identity.sql` applies cleanly on PostgreSQL (verified with Testcontainers).
- [x] Argon2 hashing works behind the `PasswordHasher` port.
- [x] JWT generation and validation round-trip, and reject expired, tampered, foreign-issuer, and foreign-key tokens.
- [x] `CurrentUser` abstraction with a Spring Security adapter as the only SecurityContextHolder reader.
- [x] ArchUnit enforces: framework-free domain, no Spring Security outside infrastructure, inward-pointing dependencies.
- [x] Registration: 201 + Location, case-insensitive duplicate → 409, invalid payload → 400 with field errors, Argon2 hash persisted, no IdentitySession created, IdentityRegisteredEvent published.
- [x] Authentication: 200 with session-bound JWT, previous sessions revoked (old tokens → 401 `identity.session.revoked`), all credential failures identical 401, CurrentUser populated from the filter, IdentityAuthenticatedEvent published.

---

# Registration (implemented — Milestone 2 PR #2)

`POST /api/v1/auth/register` is the first public API. The flow establishes the project's application-service and domain-event patterns:

```text
POST /api/v1/auth/register
  → request DTO validation (Jakarta, shared ApiError contract)
  → RegisterIdentityCommand
  → RegisterIdentityApplicationService (single transaction)
      → duplicate email? → DuplicateEmailException (409)
      → create Identity, Credentials (Argon2 hash), UserProfile
      → publish IdentityRegisteredEvent
  → 201 Created, Location: /api/v1/identities/{identityId}
```

Decisions recorded during implementation:

- **201 + Location header** pointing at `/api/v1/identities/{identityId}`, REST-conventional even though the GET endpoint does not exist yet — the registration contract will not change when it does. No JWT is returned; registration does not log the user in, and no IdentitySession is created.
- **Three-layer validation**: request DTO (shape: not blank, email format, display name 2–50, password 8–128), application service (email uniqueness), domain (normalization, invariants). Password complexity rules beyond length are deferred.
- **Race-safe duplicates**: the unique index is the authority. Concurrent registrations that pass the precondition check are caught at flush and translated to the same 409.
- **Dispatcher implementation**: `DomainEventPublisher` (framework-free port in common, with the `DomainEvent` contract) is backed by an adapter delegating to Spring's in-process event machinery; subscribers use `@EventListener`. The domain never sees Spring (ADR-005). `IdentityRegisteredEvent` carries only the `IdentityReference` and timestamp — no PII — and is published inside the registration transaction; transactional-consumer semantics get decided when the first real consumer appears (RFC-004).
- **Raw passwords never reach logs**: command and request DTOs redact the password in `toString`, hashes stay inside the identity module, error messages never echo addresses.
- `PublicEndpoints` gains exactly `/api/v1/auth/register` — deny-by-default means the whitelist grows one endpoint at a time, not by `/auth/**`.
- `AuditableEntity` implements Spring Data's `Persistable` (isNew = createdAt absent): with domain-assigned UUIDs, saves would otherwise run through merge — firing lifecycle callbacks on an internal copy and costing an extra SELECT per insert.

# Authentication (implemented — Milestone 2 PR #3)

`POST /api/v1/auth/login` authenticates registered users and returns an `AuthenticationResult` (identityId, displayName, token, expiresAt, refreshToken — null for now — and authorities), keeping the controller a thin mapper.

## Session-bound JWTs

JWTs are never revoked; IdentitySessions are. The trust chain is:

```text
JWT (sessionId claim) → IdentitySession (revocable, durable) → Identity
```

Every token carries a `sessionId` claim binding it to the login session it was issued for. The authentication filter validates the token cryptographically (stateless), then checks the session is active and belongs to the token's identity via `IdentitySessionQueryService` — the identity module's public boundary, since other modules never touch its repositories. A revoked session invalidates every token issued for it: no blacklist, no token store, no cache. The cost is one session lookup per authenticated request — the deliberate price of enforceable revocation, logout, and forced sign-out.

## Login flow

One transaction: normalize email → load profile → verify Argon2 → load identity → require ACTIVE → revoke all active IdentitySessions (**single active session per identity** is a business rule) → create the new session (userAgent and remote address captured when available; `lastAuthenticatedAt` = `lastSeenAt` = now) → issue the session-bound JWT → publish `IdentityAuthenticatedEvent` (IdentityReference only, no PII).

## Indistinguishable failures

Unknown email, wrong password, and disabled identity all produce the identical 401 (`identity.credentials.invalid`, "Invalid email or password"). The unknown-email path still pays a full Argon2 verification against a constant timing-mask hash so response timing does not reveal whether an address is registered. Logs use identityId where known and never contain emails, passwords, hashes, or tokens.

## Filter and error codes

Requests without a bearer token pass through anonymously (public endpoints keep working); protected endpoints then answer 401 (`auth.unauthorized`) via the shared-format entry point. A presented but invalid token → 401 `identity.token.invalid`; expired → `identity.token.expired`; token of a revoked session → `identity.session.revoked` (distinct so clients can show "signed in elsewhere"). The filter is the only component that reads tokens; it publishes `IdentityPrincipal` into the security context, and business code keeps seeing only `CurrentUser`.

## Interim authority rule

Until role assignment exists, every registered identity authenticates with the implicit `USER` authority — in the token, the result, and the security context. Guests and real role persistence come later.

# Authorization (implemented — Milestone 2 PR #4)

Policy-based, code-defined, deliberately not a database-backed RBAC engine: easy to understand and test, deterministic, and free of administrative complexity the platform does not need yet. When organizations, custom roles, or multi-tenancy arrive, the mapping evolves into a persisted policy engine.

## Permission model

`Permission` covers only functionality that exists: `QUIZ_VIEW`, `QUIZ_CREATE`, `QUIZ_EDIT`, `QUIZ_DELETE`, `QUIZ_HOST`, `USER_PROFILE_READ`, `USER_PROFILE_UPDATE`. Permissions are never persisted — they are derived from roles through the explicit matrix in `RolePermissions` (a domain rule, pinned by tests so any change is a conscious decision):

```text
ADMIN       → all seven
QUIZ_MASTER → QUIZ_VIEW, QUIZ_CREATE, QUIZ_EDIT, QUIZ_HOST
USER        → QUIZ_VIEW, USER_PROFILE_READ, USER_PROFILE_UPDATE
```

Roles are additive: an identity's permissions are the union over all roles it holds (a real quiz master will also hold USER once role assignment exists).

## AuthorizationService

The single place authorization decisions are made — application services in every module (Quiz, Session, Media later) call `authorize(currentUser, permission)`; nobody writes `hasRole` checks and controllers contain no authorization logic. Anonymous callers → 401; authenticated callers lacking the permission → 403 (`auth.permission.denied`). Successful authorization publishes `IdentityAuthorizedEvent` (IdentityReference + Permission + occurredAt, no PII); denials publish nothing.

The service is framework-independent — only `CurrentUser` in, decision out — and lives in the identity application layer (not the domain) because ADR-005 reserves event publication for application services; the policy itself stays in the domain.

## Demonstration endpoint

`GET /api/v1/users/me` (bearer-authenticated) exercises the full architecture: filter → CurrentUser → application service → `authorize(USER_PROFILE_READ)` → response with identityId, identityType, roles, and derived permissions. OpenAPI documents the `bearerAuth` security scheme.

With this PR the identity bounded context is feature complete for v1 (registration, authentication, authorization).

## Later

- `PublicEndpoints` currently lists exactly `/api/v1/auth/register` and `/api/v1/auth/login`.
- Proxy-aware client addresses (X-Forwarded-For) once a reverse proxy fronts the API.
- Role assignment/administration; guest authorization rules (RFC-004).
- Pre-v1 refinements agreed in review: `Email` as a first-class value object; `DomainEvent` gains `eventId` and `eventVersion` alongside `occurredAt` (replay, audit, event evolution); `DomainEventPublisher` accepts a list of events so aggregates can emit several per operation; registration response gains the identity `status` field.
- Guest identity issuance for the session join flow (RFC-004).
- Refresh tokens bound to `IdentitySession.refreshTokenHash`; refreshes move `lastAuthenticatedAt`.
- `IdentityStatus` expansion (LOCKED, PENDING_VERIFICATION) and the reserved SYSTEM identity type.
