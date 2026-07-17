# QuizChef v2.0.0 — Architecture Overview & Phase 2 Retrospective

**Status:** Milestone snapshot (frozen at tag `v2.0.0-phase2-complete`)

**Date:** 2026-07-18

**Audience:** anyone joining the project — or returning to it — who wants the whole system in one sitting before reading code. The living documents remain authoritative as the code moves on: [ARCHITECTURE.md](ARCHITECTURE.md) for principles and boundaries, the [RFCs](../rfcs/) for each subsystem's full design, the [ADRs](../adr/) for the foundational decisions. This document is deliberately a *snapshot*: what exists at v2.0.0, why it worked, and what was consciously left for later.

---

## 1. What v2.0.0 is

QuizChef is an open-source platform for building and hosting real-time quizzes — Kahoot-simple, fully self-hostable — whose first deployment powers Bible Quiz events for Bangalore Evangelical Lutheran Church. At v2.0.0 the platform is **feature-complete for a single session's entire life**:

```text
Author Quiz → Publish → Host Session → Lobby → Live Gameplay
            → Answer Reveal → Leaderboard → Session Complete
```

Every step above works end to end, for a registered host and for anonymous guest participants, over REST commands and a versioned realtime protocol, with the server authoritative over every fact that matters. Phase 1 built the backend (eight PRs, `v1.0.0-backend-complete`); Phase 2 built the frontend (five PRs) plus three small backend "bridge" PRs the frontend work surfaced. From here the project shifts to **Phase 3: Product Hardening** — production readiness, not new capability (§10).

---

## 2. System topology

```text
                 React 19 SPA  (frontend/)
                 TanStack Query · Zustand · react-router · @stomp/stompjs
                        │                        │
                REST (/api/v1/**)         STOMP over WebSocket (/ws)
                        │                        │
        ┌───────────────┴────────────────────────┴───────────────┐
        │            Spring Boot modular monolith (backend/)      │
        │                                                         │
        │  app · common · security · identity · quiz · session    │
        │  websocket · media (scaffold only)                      │
        └───────────────────────────┬─────────────────────────────┘
                                    │
                              PostgreSQL (Flyway V1–V8)
                       (MinIO reserved for media — not yet used)
```

One deployable, one database. Modules behave like services — they communicate only through each other's application layers, never repositories (enforced by ArchUnit) — but share a process, a transaction manager, and an in-process domain-event dispatcher. The frontend is **a client of the platform, never part of it**: it renders state, submits commands, and receives projections; every rule that matters is enforced server-side (ADR-006).

**Stack:** Java 21 / Spring Boot / JPA / Flyway / Testcontainers · TypeScript / React 19 / Vite / Tailwind · REST + OpenAPI-generated client types · STOMP over a SockJS endpoint's raw-WebSocket transport.

---

## 3. The workflow, mapped to the system

The single most useful mental model is the product workflow annotated with what carries each step:

| Step | Actor | Transport | Subsystems |
| --- | --- | --- | --- |
| Register / log in | host | `POST /auth/register`, `/auth/login` | identity, security |
| Author questions & quizzes | host | quiz + question CRUD, search, compose, reorder | quiz |
| Publish | host | `POST /quizzes/{id}/publish` | quiz (cross-aggregate localization check) |
| Create session | host | `POST /sessions` (publishes nothing yet — CREATED) | session ← quiz (`QuizPublicationQuery`) |
| Open lobby | host | `POST /sessions/{pin}/lobby` → `lobby.opened` | session → websocket |
| Join | participant (guest or registered) | `POST /sessions/{pin}/join` → `participant.joined` | session, identity |
| Connect / reconnect | participant | `POST /sessions/reconnect` → replay snapshot | session (`SessionSnapshotAssembler`) |
| Start | host | `POST /sessions/{id}/start` → `session.started` | session |
| Open question | host | `POST /sessions/{id}/questions/start` → `question.started {endsAt}` | session ← quiz (`GameplayQuizQuery`), server timer armed |
| Read question content | any device | `GET /sessions/{id}/questions/current` (public, phase-gated) | session ← quiz (`GameplayQuestionContentQuery`) |
| Answer | participant | `POST /sessions/{id}/answers` → private `participant.answer.accepted` | session (validation, correctness, scoring — RFC-006) |
| Close | host or server timer | `POST .../questions/close` / `closeIfExpired` → `question.closed` | session |
| Reveal | host | `POST .../questions/reveal` → `answer.revealed {correctOptionIds}` | session |
| Leaderboard | host | `POST /sessions/{id}/leaderboard` → `leaderboard.updated` | session (`LeaderboardService`, projected fresh) |
| Read standings | any device | `GET /sessions/{id}/results` (public, phase-gated) | session |
| Advance / finish | host | `POST .../questions/advance` → next `question.started` or `session.finished` | session (`QuestionProgression`) |

Twenty-nine REST endpoints total; twelve realtime message types. The host drives the *pace*; the engine owns the *order*, the *clock*, the *scores*, and the *truth*.

---

## 4. Backend subsystems

### 4.1 common
The contracts everything shares: `AuditableEntity` (UUID id, audit timestamps, `@Version` optimistic locking — every aggregate; concurrent saves 409 instead of silently overwriting), the `DomainEvent`/`DomainEventPublisher` seam with its Spring adapter, and the exception hierarchy (`ResourceNotFoundException`, `ConflictException`, `ForbiddenException`, `UnauthorizedException`) that `GlobalExceptionHandler` maps to a single `ApiError` shape carrying a **stable error code** — the codes, not HTTP statuses or messages, are what clients switch on.

### 4.2 identity
Who someone is. Aggregates: `Identity` (registered or guest), `Credentials` (Argon2 behind a `PasswordHasher` port), `UserProfile`, `IdentitySession`. Authentication issues **session-bound JWTs**: every token carries a `sessionId` claim resolved against a live `IdentitySession` per request, so revoking the session kills the token — no blacklist. Authorization is a code-defined role→permission matrix (`RolePermissions`, pinned by tests) consulted through `AuthorizationService`. Other modules reference an identity only through `IdentityReference` (id + type) — never credentials, profile, or sessions. Known v1 gap: registration and login grant only `USER`; there is no API path to `QUIZ_MASTER` (§10).

### 4.3 quiz
Content and its lifecycle. Two deliberately **separate aggregates**: `Quiz` (metadata, settings, visibility, and an ordered composition of question *references*) and `Question` (type, difficulty, options, tags) — questions are reusable across quizzes and private to their author. Content and structure are split for i18n: text lives in localization element collections (`QuizLocalization`, `QuestionLocalization`, `OptionLocalization`) with a per-aggregate configurable default language; correctness lives on `Option`, never on a translation. Lifecycle is draft-first: content edits are DRAFT-only, publishing checks every attached question is localized in the quiz's default language, published content is immutable, archive is the only exit. The module exposes four narrow application-layer boundaries to the session engine: `QuizPublicationQuery` (may this run?), `GameplayQuizQuery` (ids + correctness + difficulty, to score), `GameplayQuestionContentQuery` (prompt + options + explanation, to display), and the authoring/search read services behind the REST API.

### 4.4 session
The live engine — the heart of the product; RFC-004 is its full story. Two aggregates:

- **`Session`** — one run of a published quiz: PIN, host, lifecycle `CREATED → LOBBY → IN_PROGRESS → FINISHED → ARCHIVED`, execution settings, the ordered roster (immutable `ParticipantKey`s only), and the execution pointers: `currentQuestionId` (a content id, never an index), `currentPhase`, `currentQuestionTimer`. It references content by **`publishedQuizVersionId`** — the exact immutable content executed, revision-safe by construction.
- **`Participant`** — a durable player (ADR-003): exactly one of an `IdentityReference` or a guest reconnection token, a state machine that survives disconnects, a cached `totalScore`, and its owned answers. *A player joins a session, not a socket.* Disconnects mark; they never delete.

Inside `IN_PROGRESS`, gameplay is a phase machine — every transition an aggregate method that throws on an illegal move:

```text
(no phase) → QUESTION_OPEN → QUESTION_CLOSED → ANSWER_REVEALED → LEADERBOARD ─┐
                 ▲                                                            │
                 └────────────────── next question ◄──────────────────────────┘
                                        │ (quiz exhausted)
                                        ▼
                                    FINISHED
```

The **server owns the timer**: opening a question builds a `QuestionTimer` from the shared `Clock` and arms a scheduled close; expiry and a manual host close converge on one idempotent path, so a late-firing timer is a harmless no-op. **Answer submission** is where input becomes score, entirely on server authority: participant exists and is connected, question open and the one in play, not already answered, options valid — then the server stamps response time, decides correctness against `GameplayQuizQuery`'s correct sets, computes points (RFC-006: base + speed bonus × difficulty multiplier, pure functions, policy as a value object), and records the answer. The acknowledgement carries **no score**. The leaderboard is always **projected fresh** from cached scores, never persisted. Reconnection replay (`SessionSnapshotAssembler`) rebuilds everything a returning participant needs — phase, question, remaining clock, own submission, score, standings — from the live aggregates.

### 4.5 websocket
Transport only (ADR-004): the session module never learns a connection exists. `RealtimePublisher` is the outbound port; the STOMP adapter behind it is the only class touching `SimpMessagingTemplate`. `SessionRealtimeProjector` listens to domain events and `SessionProtocolMapper` **projects** them onto the wire protocol — projections, not serialized domain events (§6). Inbound commands are *defined* (sealed command types) but not *handled*: all commands enter over REST today, the one gap keeping RFC-005 at Accepted rather than Implemented.

### 4.6 security
The HTTP edge: JWT filter → session-bound validation → `CurrentUser`, the `PublicEndpoints` whitelist (auth, health, docs, the WS handshake, and the anonymous-friendly session paths: summary read, join, reconnect, answers, current-question, results), and the 401/403 `ApiError` entry points. Authorization *decisions* live in identity's `AuthorizationService`, called from application services — never in controllers, never here.

### 4.7 app & media
`app` is the Spring Boot launcher plus the cross-module integration tests (Testcontainers Postgres) and the OpenAPI export test that feeds the frontend's generated types. `media` is an empty scaffold — RFC-007 (MinIO-backed media storage) is designed but deliberately unbuilt.

---

## 5. Frontend architecture

RFC-009 is the full story; the shape in brief. **The backend's discipline, translated:** components render, hooks coordinate, services communicate —

```text
Component → Hook → Service (api/* | RealtimeClient) → Backend
```

No component touches axios or STOMP. Request/response types are **generated** from the backend's exported OpenAPI document (drift becomes a compile error, not a runtime surprise); the realtime protocol types are hand-written *by design*, because RFC-005 freezes them as a versioned contract.

**Feature modules.** Cross-cutting folders (`components/`, `hooks/`) are generic by rule; every real feature lives in `features/<name>/` with its own query hooks, orchestration hooks, presentational components, and a query-key registry so invalidation can never drift from a key by a typo. Three exist: `quizzes/` (authoring), `sessions/` (hosting: dashboard, creation, lobby), `gameplay/` (both roles' live play and results).

**The gameplay FSM.** One derived value drives every gameplay screen:

```text
LOBBY → COUNTDOWN → QUESTION_OPEN → WAITING → ANSWER_REVEALED → LEADERBOARD → … → FINISHED
```

`useGameplayState` computes it from two queries (session summary + current question); it is deliberately **not stored anywhere** — a stored copy of a derivable value is a drift waiting to happen. Host (`useGameHost`) and participant (`usePlayerGameplay`) orchestration are separate hooks over a shared core (`useGameplay`), because their permissions genuinely differ; they share every *presentational* component (the host renders the answer grid read-only to see what players see).

**Reconnect-first design.** Every gameplay mount is treated as a possible return: the participant path calls the server's replay contract (`reconnect`) before rendering live content — on first join (which is also what marks the participant connected), on refresh, and whenever the realtime connection recovers — seeding "what did I already submit" from the snapshot rather than local history. Completion screens render purely from reads (summary + results), which is precisely why refresh-during-results recovery is trivial: no event history required.

**Client-side facts get client-side stores; ids only.** Two small persisted Zustand stores exist solely because the backend has no corresponding query yet: `hostedSessionsStore` (which sessions this browser created) and `playerSessionStore` (which PINs this browser joined, with the guest reconnection token). Both hold **only identifiers** — the data behind them stays in TanStack Query.

---

## 6. The realtime model

RFC-005 defines a versioned wire protocol, not a message bus:

- **Envelope:** `protocolVersion` (currently 1 — bumped only on breaking change), `messageId`, `sessionId`, `occurredAt`, `type`, `payload`.
- **Vocabulary:** stable dotted names (`participant.reconnected`, `question.started`) — never backend class names, so a refactor can never break a client.
- **Topics:** `/topic/session/{id}` (broadcast), `/topic/participant/{id}` (private), `/topic/host/{id}` (host channel), built in exactly one place per side (`Topics` / `SessionSubscriptions`).
- **Projections, not domain events.** A protocol message is the *public representation* of an internal fact, mapped through one seam. Domain events may carry what clients must never see; the projection is the frozen contract. (`SessionCreatedEvent` is deliberately not projected — it has no audience yet.)

**Events are notifications; reads are the truth.** The frontend's one realtime pattern, used identically by the lobby, gameplay, and results: an event says *that* something changed, and invalidates the relevant query; the refetch learns *what* it now is. Event payloads are never a data source once a read exists to reconcile against — a client that missed events (refresh, reconnect) converges to the same state as one that saw them all, by construction.

**What crosses the wire, when** — the ADR-006 gating discipline in one table:

| Fact | Earliest moment it is visible | Enforced by |
| --- | --- | --- |
| Question content (prompt, options) | `QUESTION_OPEN` — never while probing a session pre-start | `CurrentQuestionQueryService` (409 before) |
| Remaining time | while open, as the server's `endsAt` — clients render, never decide the close | timer on the aggregate; `question.closed` is the only close signal |
| Correct option ids | `ANSWER_REVEALED` — via the event *and* the content read, gated identically | `SessionProtocolMapper` + `CurrentQuestionQueryService` |
| Author's explanation | `ANSWER_REVEALED` — it routinely gives the answer away | stripped server-side pre-reveal |
| Scores / standings | `ANSWER_REVEALED` at the earliest — mid-question standings would leak who answered correctly | `SessionResultsQueryService` (409 before) |
| A participant's own submission ack | immediately, **privately**, and scoreless | published to the participant topic only |

**The remaining transport gap:** inbound commands run over REST; STOMP `@MessageMapping` handlers and per-message subscription/command authorization are designed but unbuilt — the single item keeping RFC-005 at *Accepted*.

---

## 7. State ownership

The load-bearing table, final form. Each kind of state has exactly one owner; the same datum never lives in two stores:

| State | Owner |
| --- | --- |
| Authentication (JWT, session-expired flag) | Zustand (`authStore`, persisted) |
| Current route | React Router |
| Backend resources (user, quizzes, questions, sessions) | TanStack Query |
| Live projections (session summary, current question, results/leaderboard) | TanStack Query, invalidated by realtime events |
| Realtime connection status | Zustand (`connectionStore`) |
| Transient lobby presence (event-built roster) | Realtime only, component-lifetime |
| Hosted-session ids / joined-session records | Zustand, persisted, **ids only** |
| Gameplay phase | Derived — computed, never stored |
| In-progress answer selection, modals, form inputs | React component state |

The proof of the rule has held since PR #1: `useCurrentUser` is a query; the auth store holds only the token. Four feature PRs later, no datum has two homes.

---

## 8. Security model

- **Authentication:** stateless JWTs bound to server-side identity sessions — revocation works without a blacklist. Passwords Argon2-hashed behind a port. Indistinguishable login failures with timing masking.
- **Authorization:** explicit permission checks (`QUIZ_VIEW/CREATE/EDIT/HOST`, …) in application services; host-only session commands additionally assert host *ownership*. Controllers never decide anything.
- **Anonymous guests are first-class** (ADR-003): join, reconnect, answer, and the gameplay reads work without an account. What gates them is possession of an unguessable session id or PIN plus the server's phase-gating (§6) — anonymous access to a *running* session's public facts, never to content ahead of play.
- **Public surface is a single whitelist** (`PublicEndpoints`), commented per-entry with its rationale; everything else authenticates. One learned subtlety recorded in RFC-004: the whitelist is method-agnostic, so public reads get their *own paths* rather than sharing a path with an authenticated command.
- **Ownership privacy:** questions are invisible to non-owners (404, not 403 — no existence leak); private quizzes likewise.

---

## 9. Testing

**404 automated tests at this milestone: 309 backend (55 classes), 95 frontend (17 files).** The strategy, per layer:

- **Backend units** exercise aggregates and services directly — domain invariants (every illegal transition throws), scoring math, phase gating — with mocked ports and a fixed `Clock`; time never flakes because time never really passes.
- **Backend integration** (Testcontainers Postgres, MockMvc, mocked STOMP template) walks real workflows end to end: the full game (create → lobby → join → answer → score → reveal → leaderboard → advance → finish), reconnection mid-game, migration correctness on seeded data, OpenAPI documentation presence, and the realtime projections captured off the broker template.
- **Frontend tests** mount the *real route table inside the real provider stack* against MSW (network mocked at the boundary — the app's actual axios/interceptor stack runs) and a scriptable **fake STOMP transport** injected through `RealtimeClient`'s factory seam, so realtime scenarios (join events, closes, broadcasts, connection loss and recovery) are driven by hand and asserted through the UI. Refresh recovery is tested as what it really is: a fresh mount with no event history.
- **Two deliberate testing choices** that paid off: no fake timers anywhere (fixtures hand the UI an `endsAt` already in the past instead — deterministic, immune to the `useFakeTimers`+`waitFor` deadlock class), and hook-level testing where jsdom can't express the gesture (dnd-kit reordering).

---

## 10. What proved successful — Phase 2 retrospective

Decisions worth keeping, with the evidence that they earned it:

1. **Server-authoritative everything (ADR-006).** The single highest-leverage decision. Because correctness, scores, timing, and progression only ever lived server-side, adding the reveal/results UI in PR #5 required *zero* trust changes — the phase-gating table in §6 fell out of existing structure. The frontend never once needed a rule "ported" to it.
2. **Durable participants (ADR-003) + a replay contract.** Refresh and reconnect — the failure modes that sink live-event apps — were designed in before any transport existed. The result: participant refresh recovery in the UI was a *fixture seed*, not a feature.
3. **Transport independence (ADR-004) with projections, not domain events.** The session module still does not know WebSockets exist. The stable dotted vocabulary meant four frontend PRs of realtime work without one backend refactor breaking a client.
4. **Generated API types.** `OpenApiSpecExportTest → openapi.json → api.gen.ts` turned every backend contract change into a frontend compile error. Drift simply never happened.
5. **One state owner per datum.** The RFC-009 table was written before the first feature page and survived all of Phase 2 unamended in spirit — additions only. "Where does this state live?" never became a debugging question.
6. **A single realtime client and a single FSM.** One `RealtimeClient` (reconnect + resubscribe solved once, below feature code) and one derived `GameplayPhase` (no component ever re-inferring game state) kept the most concurrency-prone UI in the app boring to extend: PR #5 added three phases and zero subscriptions.
7. **The bridge-PR pattern.** Three times the frontend hit a genuine contract gap (authoring composition; participant-facing question content; the results read). Each time: pause, build a small read-side backend PR at the application-layer boundary, regenerate types, continue. No invented client contracts, no mocked-forever endpoints, no scope creep — and each bridge is documented in the RFC it extends.
8. **Application-layer module boundaries.** Cross-module needs (session → quiz) always became a named query service (`QuizPublicationQuery`, `GameplayQuizQuery`, `GameplayQuestionContentQuery`) rather than a shared repository — so "what can the session engine see?" is answerable by reading four small files.
9. **RFC-per-milestone with a real lifecycle.** Every subsystem has a document that *matches the code*, because status flips ride the PR that causes them and implemented sections are written from what shipped. Onboarding equals reading; this document could be concise because the RFCs carry the depth.
10. **Vertical-slice PRs with honest scope notes.** Each feature PR ended with its contract adaptations and known gaps stated in the PR body and RFC — which is why the deferred-work register below could be assembled from the record instead of from memory.

---

## 11. Deferred work register

Everything consciously postponed, consolidated from the RFCs' Future Work sections and mapped to the Phase 3 hardening themes. Nothing here is a surprise; every item is already documented at its source.

### Backend — contract completions
| Item | Why deferred | Unblocks |
| --- | --- | --- |
| Session list (+ roster) read endpoints | church-scale workaround acceptable | retiring `hostedSessionsStore`, lobby's id-only roster rows |
| Session lifecycle timestamps (started/finished) | needs domain + migration work | duration & completion time on summaries |
| Per-answer verdict on the wire | set-equality display sufficed | deleting `verdictFor` (flagged in RFC-009 Risks) |
| Inbound STOMP commands + per-message authorization | REST commands suffice at scale | RFC-005 → Implemented; lower command latency |
| Single-active-connection policy | multi-device joins are rare | "newer device wins" invalidation |
| **Role persistence** (no API path grants `QUIZ_MASTER`) | test tokens minted directly | real host onboarding — *repeatedly hit; prioritize early* |
| Logout / refresh tokens / guest identity issuance (RFC-002) | session revocation covers the risk | full auth lifecycle |
| Quiz/session revisions (own RFC), `LanguageCode` → common, `SessionReadyEvent`, `QuestionProgressChangedEvent` | v1 doesn't need them | re-publishing edited quizzes; analytics/spectator seams |
| Media storage (RFC-007, module scaffold) | out of Phase 1/2 scope | images/audio on questions |

### Frontend — hardening (Phase 3 themes)
| Item | Theme |
| --- | --- |
| Route-level code splitting (~964 kB single bundle) | performance |
| WCAG audit beyond accessible-by-default primitives | accessibility |
| UI-string i18n (content is already multilingual server-side) | launch readiness |
| PWA/offline beyond the indicator; richer reconnect UX | resilience |
| Registration page (endpoint + schemas exist) | onboarding |
| Connect-time JWT on the realtime channel | security (pairs with per-message authz) |
| Per-browser registries (hosted/joined) → server reads | correctness across devices |

### Platform & operations
| Item | Theme |
| --- | --- |
| OpenAPI drift check in CI (regenerate + fail on diff) | CI/CD |
| Deployment automation (RFC-008), observability (structured logs, metrics, tracing) | operations |
| Rate limiting, abuse prevention, `X-Forwarded-For` handling, security review | security |
| Load testing the realtime path; broker options beyond the simple broker | scalability |

---

## 12. Baseline facts

| Fact | Value |
| --- | --- |
| Tag | `v2.0.0-phase2-complete` (this snapshot) |
| Prior milestones | `v0.1.0-foundation` … `v1.0.0-backend-complete` (8 tags) |
| Backend modules | app, common, security, identity, quiz, session, websocket (+ media scaffold) |
| REST endpoints | 29 under `/api/v1` |
| Realtime message types | 12 (protocol version 1) |
| Database migrations | Flyway V1–V8 |
| Automated tests | 404 (309 backend / 95 frontend) |
| ADRs / RFCs | 6 ADRs; RFC-001…RFC-009 (007/008 designed, unbuilt) |
| Frontend routes | 16 (public play + PIN route; authenticated authoring/hosting/gameplay) |
| Frontend bundle | ~964 kB minified (code splitting: Phase 3) |
