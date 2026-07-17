# QuizChef Architecture

**Version:** 1.0.0

**Status:** Living Document

**Last Updated:** 2026-07-14

---

# 1. Purpose

This document is the architectural source of truth for QuizChef.

Every contributor (human or AI) MUST read this document before making architectural decisions.

This document explains:

- Why QuizChef exists
- What problems it solves
- Architectural philosophy
- Engineering principles
- Module boundaries
- System design
- Constraints
- Coding philosophy

Implementation details belong in their respective documents.

---

# 2. Vision

QuizChef is an open-source platform for building and hosting engaging real-time quizzes.

The first deployment powers Bible Quiz events for Bangalore Evangelical Lutheran Church.

The long-term vision is to become an extensible quiz platform for churches, schools, companies, conferences and communities.

QuizChef should feel as simple as Kahoot while remaining completely self-hostable.

---

# 3. Mission

Enable anyone to create engaging quizzes in minutes.

Provide an exceptional live experience for participants.

Remain easy to deploy, maintain and extend.

---

# 4. Design Goals

Priority order.

1. Simplicity
2. Reliability
3. Maintainability
4. Extensibility
5. Performance
6. Scalability

Scalability should never come at the cost of developer productivity unless necessary.

---

# 5. Engineering Principles

QuizChef follows these principles.

## 5.1 Modular Monolith

QuizChef is NOT a microservice application.

It is a modular monolith.

Reasons:

- Faster development
- Easier deployment
- Easier debugging
- Lower infrastructure cost
- Better developer experience

Modules should behave like independent services while sharing one deployment.

---

## 5.2 Domain First

The domain model drives the architecture.

Controllers do not define the system.

Database tables do not define the system.

The business domain defines the system.

---

## 5.3 API First

Public APIs are contracts.

Controllers should never expose entities.

Every endpoint should be intentionally designed.

---

## 5.4 Open Source First

Every architectural decision should assume:

Someone else will read this code.

Someone else will contribute.

Someone else will deploy it.

Code should be optimized for readability.

---

## 5.5 Convention Over Configuration

Reasonable defaults should exist.

Users should configure only what they must.

---

## 5.6 Testability First

Every service should be testable.

Every module should be independently testable.

Dependencies should be injectable.

---

## 5.7 Security by Default

Authentication is mandatory where required.

Authorization is explicit.

Sensitive information is never logged.

Validation occurs at the API boundary.

---

## 5.8 Feature Completeness

Shipping fewer polished features is preferred over shipping many incomplete ones.

---

# 6. Architecture Overview

                   React Application

                          │

          REST API + WebSocket (STOMP)

                          │

          Spring Boot Modular Monolith

┌─────────────────────────────────────────────┐

App

Identity

User

Quiz

Session

Media

Security

WebSocket

Common

└─────────────────────────────────────────────┘

                PostgreSQL

                     │

                  MinIO

---

# 7. High Level Components

Frontend

Responsible for:

- UI
- Routing
- Authentication
- WebSocket Client
- State Management

Backend

Responsible for:

- Business Logic
- Validation
- Authentication
- Authorization
- Session Management
- Scoring

Database

Responsible for:

- Persistence

Object Storage

Responsible for:

- Images
- Audio
- Video

---

# 8. Core Domain Model

The identity hierarchy:

Identity

↓

User

↓

Participant

## Identity

Who someone is.

Every actor in the system has an Identity.

An Identity is either a Guest Identity or a Registered User.

Guest Identities are short-lived and exist only to play.

## User

A registered Identity.

Represents:

Authentication

Registration

Profile

Roles

Email

Password

Owned by the Identity module (with user-facing features in the User module).

## Participant

Someone playing in a live session.

Represents:

Joining a quiz

Display name

Total score (cached sum of answer points)

Connection status

Active connection (optional)

Session

Ranking

Answers

Owned by the Session module.

A Participant is always backed by an Identity — either a Guest Identity or a Registered User.

Modules refer to an Identity through IdentityReference (identityId + identityType) — who is acting, never credentials, profile, or sessions.

A Participant is scoped to a single session. The same person joining two sessions is two Participants.

Guest play and registered play share one code path.

## Durable Participants

A Participant is a durable session entity. Connections are ephemeral.

A player joins a quiz session, not a WebSocket connection. The active connection is an optional property of the Participant, never its identity.

The active connection is modeled as a ParticipantConnection: active transport, reconnect, heartbeat, disconnect. It is deliberately not called "presence" — presence has a distinct meaning in distributed systems.

A Participant survives network interruptions, browser refreshes, app crashes, device sleep, and network switches.

Disconnects mark the Participant disconnected. They never delete it.

Participant lifecycle:

Created

↓

Connected

↓

Disconnected

↓

Reconnected

↓

Finished

Reconnection restores score, answers, current question, and leaderboard position.

Registered users reconnect through their identity. Guests reconnect through a participant token stored on the client.

Joining with the same identity from a new device invalidates the previous connection (single active session policy).

---

# 9. Module Responsibilities

## App

Spring Boot launcher.

Contains no business logic.

---

## Common

Shared utilities.

Exceptions.

Constants.

Utility classes.

Shared DTOs.

Domain event contract (DomainEvent) and event dispatcher.

Base entity (AuditableEntity: UUID id, timestamps, and optimistic locking — every aggregate carries a version; concurrent saves receive 409 instead of silently overwriting each other).

---

## Identity

The identity bounded context (previously named Auth; renamed because authentication is just one thing an identity does).

Identity lifecycle (registered and guest).

Credentials (password hashes behind the PasswordHasher port).

User profiles (email is the login identifier).

Identity sessions (durable login sessions).

Roles, permissions, and the policy-based AuthorizationService (permissions are derived from roles in code, never persisted).

JWT infrastructure.

Guest Identity issuance.

CurrentUser port — the framework-independent request context all business services depend on.

---

## User

User-facing account features on top of the Identity module.

Preferences.

Quiz history.

(Profiles and roles live in the Identity module.)

---

## Quiz

Quiz management.

Authoring API (create, read, update, publish, archive — draft-first: content and settings change only while DRAFT, published quizzes accept nothing but visibility, archiving is terminal and deletion does not exist). Ownership comes from CurrentUser; mutations are owner-only, and private quizzes are invisible to everyone else.

Questions — separate reusable aggregates, never owned by a quiz. Quizzes compose them by id through `QuizQuestion` (an embeddable element inside the Quiz aggregate, never an independently-repositoried entity — ordering invariants belong to the root that owns them).

**Composition ordering is explicit, never insertion-order.** `QuizQuestion.displayOrder` is a stored integer position, assigned by the aggregate (append = max + 1 on attach; reassigned `1..n` on reorder) — the database and the domain agree on order without ever depending on collection iteration order. Attach (`POST /quizzes/{id}/questions`), detach (`DELETE .../questions/{questionId}`), and reorder (`PATCH .../questions/order`) are all owner-enforced, atomic application-service operations: attach accepts draft or published questions (only archived is rejected — an author composes while still refining), while detach and reorder are draft-quiz-only, matching every other authored-content change. A published quiz may gain questions but never lose or reorder them, so participants who already relied on the composition are never surprised.

Question library API (create, read, update, publish, archive — owned by their author, draft-editable, immutable once published, retained when archived so existing published quizzes keep working; questions never know which quizzes use them). "My Quizzes" and the question library are both owner-scoped, filtered, and paginated reads (`GET /quizzes/mine`, `GET /questions`) — every filter is an optional `Specification` predicate composed onto a mandatory ownership predicate, so cross-author visibility is structural, not a post-filter. Sorting is allow-listed to root-entity columns only (content like a title lives in a per-language child collection, not a column); `@BatchSize` on the localization/composition/tag element collections keeps a page of results to a handful of queries instead of one per row.

Tags — their own aggregate (id + normalized name); questions hold tag ids, so synonyms, hierarchies, and organization vocabularies can grow without touching questions.

Question source metadata (MANUAL / AI / IMPORT) — provenance for the future generation and import features; no behavior varies by it.

Typed questions (single choice, multiple choice, true/false) with per-type structural rules.

Options (language-neutral: id, correctness, order — participants answer with option ids, so gameplay never depends on language).

Multilingual content (LanguageCode value object; quiz, question, and option text live in per-language localizations owned by their aggregate; each quiz and question has a configurable default language whose localization always exists; a translation is whole or absent). Participants choose their language per session (session module) — identities carry no content language.

Bible references (deliberately not localized — the canonical reference is language independent).

Media references (shared across all translations).

Validation (aggregates enforce their own invariants).

---

## Session

Live quiz execution.

Two aggregates. **Session** — the run of a published quiz: PIN, host, lifecycle (CREATED → LOBBY → IN_PROGRESS → FINISHED → ARCHIVED), execution settings, and the ordered roster. It references the exact published quiz content it executes by a revision-safe id (`publishedQuizVersionId`), never "the latest quiz"; it owns the roster (ordering, membership, and per-session uniqueness of each participant's identity/guest token) but not quiz content or participant state.

**Participant** — a durable player (ADR-003): survives disconnects, refreshes, and device switches. Identified by exactly one mechanism — a registered identity or a guest reconnection token, never a display name or a connection. Owns its own answers and cached score; connectivity is derived from its lifecycle state, never a stored flag.

Orchestration (not CRUD): create → open lobby → join → reconnect → start, one application service per step, publishing domain events that reach clients automatically through RFC-005. Host operations (create/lobby/start) require QUIZ_HOST *and* host ownership; join/reconnect/read are anonymous-friendly so guests are first-class.

PIN generation through a `SessionCodeGenerator` port (six secure-random digits, never timestamps or sequences; unique among active sessions, reusable after archival) — swappable for room codes or invitation codes without touching orchestration.

Guest lifecycle: an anonymous join creates a guest Participant and issues a reconnection token (returned once); the guest presents it to reconnect. A registered join is backed by the caller's identity. Both are one code path (ADR-003).

Participants (backed by a Guest Identity or a Registered User).

Guest participant tokens (opaque reconnection credentials, never a business identity).

**Execution engine (ADR-006, RFC-004).** Live play is a server-authoritative phase machine inside IN_PROGRESS: QUESTION_OPEN → QUESTION_CLOSED → ANSWER_REVEALED → LEADERBOARD → … → FINISHED. Every transition is a Session aggregate method; illegal transitions throw. Host progression commands (start / close / reveal / show-leaderboard / advance) are one application service each over REST; the engine chooses question order while the host drives the pace, and advancing past the last question finishes the session. The server owns the question timer — built from the shared `Clock`, armed by a scheduler that closes an expired question idempotently against a manual close. No client ever computes timing, correctness, or score.

**Answer submission** is validated entirely server-side: the participant must exist, be connected, and answer the open question exactly once with valid options (a host has no participant and cannot answer). The server stamps the response time from its `Clock`, decides correctness itself, computes the points, and records a durable answer that maintains the participant's cached score (ADR-003). The acknowledgement carries no score and is private to the submitter.

**Scoring and leaderboard (RFC-006).** Framework-independent domain services: a `ScoringService` driven by a swappable `ScoringPolicy` (base + speed bonus × difficulty multiplier, never negative), and a `LeaderboardService` that projects standings ordered by score, then earliest-to-reach-it, then join order — always computed from cached scores, never persisted.

**Session recovery** (reconnection, state restoration): `SessionSnapshotAssembler` rebuilds the replay snapshot — current phase, question, remaining time, the participant's own answer and score, and the leaderboard — so reconnecting mid-question restores active gameplay, not just lobby membership.

Transport is entirely absent from the domain (ADR-004): realtime behaviour is expressed as domain events and state; only the websocket module knows a connection exists.

---

## Media

Uploads.

Downloads.

Object storage integration.

---

## Security

Spring Security configuration.

JWT filters.

Authorization.

---

## WebSocket

Realtime communication — transport only (ADR-004/005). The one module that knows STOMP exists.

A versioned, transport-independent wire protocol (RFC-005): a `ProtocolMessage` envelope carrying `protocolVersion` (evolve clients and server independently), a stable dotted event vocabulary (`participant.reconnected`) decoupled from domain class names, centralized topics, and a reconnection-snapshot contract. Protocol events are *projections* of domain events — the public, frozen representation, never the internal event itself; domain entities are never serialized.

The outbound path: subscribe to session domain events → map to protocol messages (`SessionProtocolMapper`) → publish through the `RealtimePublisher` port → the STOMP adapter resolves the topic and sends. This now covers the whole game — lobby, lifecycle, and the gameplay events (`question.started`, `question.closed`, `answer.revealed`, `leaderboard.updated`) — plus the private `participant.answer.accepted` ack routed to the submitter's topic only (no score leaks). The port names the audience (session / participant / host), never the destination, so the transport could become SSE or MQTT with no caller change. Inbound commands run over REST today; a mirror-image STOMP inbound channel delegating to the same application services is the remaining transport work (RFC-005).

Connections are ephemeral transport. Participant state never lives here.

Expected to generalize into a transport module (websocket, sse, mqtt) as delivery mechanisms grow.

---

## Frontend (RFC-009)

The React application, in `frontend/` — a client of the platform, never part of it: it renders state, submits commands, and receives projections (ADR-006). Business rules live on the server; any optimistic UI reconciles to server state.

**The same layering as the backend.** Components render; hooks coordinate; services communicate. A component never calls axios or STOMP directly:

```text
Component → Hook → Service (api/* | RealtimeClient) → Backend
```

**Component hierarchy.** `app/` (App → Providers → Router) is the shell; `layouts/` (Public, Dashboard) frame `pages/`; `pages/` compose shared `components/` (common / forms / feedback / navigation — generic only) and, for a real feature, `features/<name>/components/` (feature-specific presentational components). Providers stack outermost-first: ErrorBoundary → QueryClientProvider → AuthProvider → RealtimeProvider → theme.

**Feature modules** (`features/<name>/`, established by quiz authoring, Phase 2 PR #2): each owns its query hooks, its orchestration hooks (the coordination layer a page actually calls — e.g. `useQuizAuthoring`, `useQuestionSelection`, `useQuizPublishing`), its presentational components, and a `queryKeys.ts` registry so a mutation's cache invalidation can never drift from a query's key. `components/` and the cross-cutting `hooks/` stay feature-ignorant by rule; anything that knows about quizzes lives in `features/quizzes/`.

**State ownership is exclusive** (RFC-009): the JWT, realtime connection status, and UI preferences live in Zustand; every server resource lives in TanStack Query; routing state lives in React Router; transient UI state lives in component state. The same datum never lives in two stores — `useCurrentUser` is a query; the auth store holds only the token.

**Optimistic updates are scoped to what's reversible.** Quiz composition (attach/detach/reorder a question) updates the cache immediately and rolls back on failure; lifecycle transitions (create, publish, archive) stay server-confirmed with no optimistic UI — the same server-authoritative spirit as ADR-006, applied to when a client is allowed to render ahead of the server at all.

**API layer.** One axios instance (base URL, JWT injection, timeout, ApiError→`ApiClientError` mapping, 401→session-expiry); one module per backend context (identity/quiz/question/session) whose request/response types are **generated from the backend OpenAPI spec** (`npm run generate:api` reads the export produced by `OpenApiSpecExportTest`) — DTOs are never hand-maintained. Retries belong to TanStack Query alone.

**Realtime lifecycle.** `RealtimeClient` wraps STOMP: explicit connect (nothing connects at boot — the feature that needs the channel owns the lifecycle; today that is the lobby, which connects on mount and disconnects on unmount), automatic reconnect with resubscription of every registered destination, heartbeats, and RFC-005 `ProtocolMessage` parsing with a protocol-version check. `SessionSubscriptions` mirrors the backend topic hierarchy — the only place destinations are built; pages never see one.

**Realtime ownership in the lobby** (Phase 2 PR #3). The lobby is a realtime application with one clean division of truth: the **session summary query establishes initial state and remains the single home of server state** (lifecycle state, participant count, settings, PIN); **realtime events are the change feed** — every lifecycle or roster event invalidates that same query, so a pushed change lands in the same cache entry a fetch would fill, never in a second copy (the RFC-009 ownership table's "live session projections" row, realized). **Transient presence** — who is visible in the roster right now — is the one thing realtime owns outright (`useParticipants`): it exists only while the lobby is mounted, is rebuilt from `participant.*` events, and is deliberately not authoritative — the server's `participantCount` stays the headline number, and participants who joined before the view subscribed render as a count row, not as fabricated entries. Nothing polls, and nothing transitions optimistically: the host's start command navigates only when the server-confirmed state says `IN_PROGRESS`, whether that truth arrived as the start response or as a `session.started` broadcast.

**Gameplay feature organization** (Phase 2 PR #4, `features/gameplay/`). One finite state machine (`useGameplayState`) derives `LOBBY | COUNTDOWN | QUESTION_OPEN | WAITING | FINISHED` from the session summary plus a new current-question read; every gameplay component renders off that single value instead of re-inferring it. `useGameplaySubscriptions` composes the lobby's own `SessionSubscriptions` topic helpers into one dispatcher — gameplay adds no new destinations, only a new consumer of the existing ones. Host and participant get separate orchestration hooks (`useGameHost`, `usePlayerGameplay`) built on the same shared `useGameplay`, deliberately never merged, even though both share presentational components (`QuestionCard`, `QuestionTimer`, `AnswerGrid` in a `readOnly` mode for the host) — their commands and permissions differ enough that a shared orchestration hook would risk one role accidentally calling the other's action.

**Realtime responsibilities in gameplay.** Same division of truth as the lobby, extended: the session summary and the new current-question query are the state; every question/answer/leaderboard-progression event invalidates both, every session-lifecycle event invalidates the summary alone. The one genuinely new responsibility is **participant recovery**: `usePlayerGameplay` calls the session module's `reconnect` endpoint (RFC-004's replay contract) on every mount that already knows a participant (fresh join or a returning browser) and again whenever the realtime connection recovers from a drop, seeding "what did I already submit" from the snapshot rather than the event stream — a participant who refreshes mid-question sees their submitted state immediately, not a blank grid waiting for an event that already happened before the refresh.

**State ownership additions.** `playerSessionStore` (Zustand, persisted, keyed by session PIN) holds a participant's join identity — mirroring `hostedSessionsStore`'s "ids only" discipline — and the FSM's `phase` is deliberately **not** stored anywhere: it is recomputed from the query cache on every render so it can never drift from the facts it summarizes. A question's in-progress answer selection lives in component state (`AnswerGrid`) until submitted, per the same table's existing "component-local UI" row.

**The completed gameplay lifecycle** (Phase 2 PR #5 — Phase 2 feature-complete). The FSM's final form, extended from PR #4 and never redesigned:

```text
LOBBY -> COUNTDOWN -> QUESTION_OPEN -> WAITING -> ANSWER_REVEALED -> LEADERBOARD -> (next question ...) -> FINISHED
```

`WAITING` narrowed to exactly the backend's `QUESTION_CLOSED` once the later phases gained real screens; the host's single action now issues one visible server command per phase (Reveal Answer -> Show Leaderboard -> Next Question / Finish Quiz) instead of PR #4's invisible chain — the frontend still never advances the game, it only asks the server to.

**Results ownership.** Standings live in one TanStack Query entry over the backend's results read (`GET /sessions/{id}/results` — added because the leaderboard previously existed only as a host-only phase-transitioning command plus a broadcast, neither of which survives a refresh or exists after FINISHED). Rankings, scores, the winner, and the podium render the server's rows verbatim — never computed, sorted, or re-ranked client-side; `answer.revealed`/`leaderboard.updated`/`session.finished` invalidate the entry and the refetch learns the new truth, the same event-then-reconcile pattern as the rest of gameplay. Both roles' completion screens render purely from the summary + results reads, which is what makes refresh-during-results recovery trivial: a fresh mount needs no event history. The one client-side computation on any results surface is a display diff of two consecutive server snapshots (score deltas, rank movement) and a set-equality verdict of the viewer's own accepted submission against the revealed correct set — both render server facts; neither invents one.

**Design tokens** (`theme/tokens.css`): semantic HSL variables consumed through Tailwind; dark mode is a `data-theme` attribute swap driven by the persisted UI preference.

---

# 10. Architectural Constraints

Modules communicate through public interfaces.

Modules never access another module's repositories directly.

Controllers never contain business logic.

Repositories never contain business logic.

Entities are persistence models.

DTOs are API contracts.

Domain state is durable. Transport state is ephemeral. The domain never depends on a transport (WebSocket, SSE, polling).

Only Application Services mutate aggregates, publish domain events, or open transactions. Everything else is read-only.

Domain events are framework independent.

---

# 11. Package Structure

Each module follows the same structure.

api

application

domain

infrastructure

Example

quiz

├── api

├── application

├── domain

└── infrastructure

---

# 12. Request Flow

HTTP Request

↓

Controller

↓

Application Service

↓

Domain

↓

Repository

↓

Database

Controllers orchestrate.

Domain models implement business rules.

Repositories persist.

---

# 13. Domain Events

State changes are announced through internal domain events.

HTTP / WebSocket

↓

Application Service

↓

Business Logic

↓

Publishes Domain Event

↓

Event Dispatcher

├── WebSocket Publisher

├── Audit Logger

└── Future subscribers (notifications, analytics)

Everything is reactive inside the application — without Kafka, RabbitMQ, or Spring Cloud. The dispatcher is in-process.

## The Event Model

Domain events are framework independent.

QuizChef defines its own contract instead of Spring's ApplicationEvent, so the domain never depends on the framework:

public interface DomainEvent {

    Instant occurredAt();

}

Examples:

QuestionStartedEvent

ParticipantJoinedEvent

ParticipantReconnectedEvent

AnswerSubmittedEvent

LeaderboardUpdatedEvent

SessionCompletedEvent

Events are pure domain concepts. The contract and the dispatcher live in the Common module.

## Rules

Only Application Services publish domain events.

Inbound commands always enter through an Application Service. The transport never orchestrates business logic.

Outbound realtime updates always leave through domain events. Domain modules never call the WebSocket module.

Subscribers perform delivery and side effects (publishing, logging). They never trigger business operations.

## What This Enables

Because every state change is an event, the session timeline can later be reconstructed — analytics, replay, moderation — without changing the domain.

This is not event sourcing. The database remains the source of truth. Events are notifications, not storage.

---

# 14. Dependency Rules

Allowed

Controller

↓

Application

↓

Domain

↓

Infrastructure

Forbidden

Controller → Repository

Repository → Controller

Infrastructure → API

Cross-module repository access

---

# 15. Technology Stack

Backend

Java 21

Spring Boot

Spring Security

Spring Data JPA

Flyway

PostgreSQL

Spring WebSocket

JJWT

Argon2 (spring-security-crypto)

MapStruct

Lombok

ArchUnit

Frontend

React 19

TypeScript

Vite

React Router

Tailwind

shadcn/ui conventions (CSS-variable tokens, cva)

TanStack Query (server state)

Zustand (client state)

Axios

STOMP.js

React Hook Form + Zod

dnd-kit (accessible drag-and-drop, keyboard-operable)

Vitest + Testing Library + MSW

openapi-typescript (generated API types)

Infrastructure

Docker Compose

GitHub Actions

MinIO

Railway

Cloudflare Pages

---

# 16. Data Storage

Relational data belongs in PostgreSQL.

Large files belong in Object Storage.

Application never stores images inside PostgreSQL.

---

# 17. Logging

Every request has

Correlation ID

User ID

Execution Time

Never log

Passwords

JWT

Secrets

Tokens

---

# 18. Error Handling

Single global exception handler.

Consistent API response format.

Never expose stack traces.

---

# 19. Security

JWT authentication.

Role-based authorization.

Input validation.

Output sanitization.

Rate limiting (future).

---

# 20. Scalability Strategy

Scale vertically first.

Scale horizontally later.

Split modules into services only when operational requirements justify it.

---

# 21. Quality Standards

Code should be:

Readable

Maintainable

Testable

Documented

Consistent

Performance is important.

Readability is mandatory.

---

# 22. Project Philosophy

Every engineer should be able to understand a feature within minutes.

Architecture exists to make development easier.

Avoid unnecessary abstractions.

Avoid premature optimization.

Prefer explicit code over clever code.

---

# 23. AI Contribution Rules

AI-generated code must follow this document.

AI must never:

Introduce new frameworks.

Replace architectural decisions.

Change module boundaries.

Generate placeholder implementations.

Leave TODO comments.

Every AI-generated change should be production quality.

---

# 24. Definition of Done

A feature is complete only if:

Code builds.

Tests pass.

Documentation updated.

API documented.

No warnings.

Reviewed.

Merged.

---

# 25. Future Evolution

Expected future modules:

Organization

Teams

Tournament

Analytics

Notifications

Question Bank

AI

Localization

Mobile

These additions must not require architectural redesign.

---

# 26. Guiding Principle

QuizChef is designed for the next contributor, not the current one.

Readable code is a feature.

Architecture should remove complexity, not introduce it.