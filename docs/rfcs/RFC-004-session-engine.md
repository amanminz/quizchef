# RFC-004 Session Engine

Status

Accepted

<!-- Draft | Proposed | Accepted | Implemented | Superseded by RFC-XXX
     Accepted — the design is agreed; the domain foundation (M4 PR #1) is
     implemented, transport/gameplay follow. Flips to Implemented when the
     session engine is feature-complete. See README.md for the lifecycle. -->

Authors

Aman Minz

Created

2026-07-14

Updated

2026-07-16

---

# Summary

Defines the Session bounded context: the **Session** aggregate (a live run of a published quiz — PIN, host, lifecycle, execution settings, and an ordered roster) and the **Participant** aggregate (a durable player that survives disconnects), their state machines, value objects, domain events, and persistence.

Milestone 4 PR #1 implements the **domain foundation only** — no REST, no WebSocket, no timers, no gameplay, no scoring. Those arrive in later PRs (transport in #1.5, gameplay and scoring after). This document is Accepted: the model is agreed and the foundation is built; it flips to Implemented when the engine is feature-complete.

---

# Motivation

Live play is the product. It is also the most concurrency-heavy, failure-prone part of QuizChef — participants lose connections, refresh browsers, switch networks, and rejoin. Getting the **aggregate boundaries and the durability model** right before any transport or timer code exists is what keeps that complexity from leaking everywhere. Two accepted ADRs already decided the hard parts; this RFC realises them:

- **ADR-003 (Durable Participants):** a player joins a session, not a socket. A disconnect marks a participant disconnected; it never deletes them or resets their score.
- **ADR-004 (Transport Independence):** nothing in the domain references a connection. Realtime behaviour is domain events and state; only the websocket module knows the transport exists.

---

# Goals

- Two clean aggregates with explicit invariants, framework-independent, transport-free.
- Durable participants: score and progress survive any disconnect, for guests and registered users alike.
- A session model that references the **exact published content it executed**, immune to later quiz edits or revisions.
- A foundation the realtime transport (PR #1.5) and gameplay/scoring PRs build on without reshaping it.

---

# Non Goals

- REST APIs, WebSocket/STOMP, host commands — transport is PR #1.5, specified in **[RFC-005 Realtime Protocol](RFC-005-websocket-protocol.md)** (the wire protocol, topics, and STOMP adapter that project the domain events defined here). RFC-004 owns the session domain; RFC-005 owns how it reaches clients.
- Timer execution, countdown, question progression, answer submission, scoring, leaderboards — later gameplay PRs (RFC-006).
- Session recovery *logic* — the `SessionRecoveryService` is designed here, implemented with transport.
- Authorization *implementation* — the future application services will consult `AuthorizationService`; documented below, not built.

---

# Proposed Design

## Session owns / Session does not own

The single most important boundary in this context.

**The Session owns:**
- its **PIN** and **lifecycle** (state machine below);
- the **roster** — which participants are in the session, their **join ordering**, and the **uniqueness** of each participant's identity or guest token *within this session*;
- the **execution pointers**: `currentQuestionId`, `currentPhase`, `currentQuestionTimer` (all model-only in this PR — populated by progression later);
- **execution settings** (`SessionSettings`).

**The Session does not own:**
- **quiz content** — it holds `publishedQuizVersionId` (a plain id), never Quiz/Question aggregates;
- **question ordering** — it points at the *current question by id*, not a positional index, so it never depends on how the quiz orders its questions;
- a **Participant's mutable state** — answers, score, connection, display name all live on the Participant aggregate;
- **connections / transport** — a Session never knows a socket exists (ADR-004).

## Why two aggregates

A Session and a Participant have different consistency needs and change at different rates. During play, dozens of participants each update their own score, answers, and connection independently and constantly; the session's lifecycle and roster change rarely. One aggregate holding both would make every answer submission contend on the session root. So:

- **Session** is the roster authority — it decides who is in, in what order, and enforces that one identity (or one guest token) appears once.
- **Participant** is its own aggregate — its answers, cached score, and connection are one small consistency boundary that a single player's actions mutate.

The roster holds participant **ids and immutable keys**, never Participant aggregates (ADR-003: the two are separate boundaries).

## Why the Session references a *published version*, not a Quiz

A running or finished session must always render the **exact content it executed against**, regardless of later edits. Quizzes today are largely immutable once published (RFC-003), and quiz/question **revisions** are a planned future RFC — the moment editing published content becomes real, "the quiz" stops being a single thing. So the Session stores `publishedQuizVersionId`: deliberately **not** `publishedQuizId`, which would imply "the latest, mutable quiz." It is a plain `UUID` (no type coupling to the quiz module); when revisions land, it resolves to the specific immutable version the session ran. This one naming decision now avoids a disruptive refactor later and keeps sessions aligned with immutable published content.

## Session aggregate

`sessionPin (SessionPin), publishedQuizVersionId (UUID), hostIdentity (IdentityReference — exactly one host, set at creation, immutable), state, currentQuestionId (UUID, nullable), currentPhase (SessionPhase, nullable), sessionSettings, currentQuestionTimer (QuestionTimer, nullable), roster (ordered SessionRosterEntry collection), version.`

Lifecycle, enforced by the aggregate — never by controllers:

```text
CREATED → LOBBY → IN_PROGRESS → FINISHED → ARCHIVED
```

- `openLobby` (CREATED→LOBBY), `start` (LOBBY→IN_PROGRESS), `finish` (IN_PROGRESS→FINISHED), `archive` (FINISHED→ARCHIVED). Any other move throws `InvalidSessionTransitionException` (409).
- `start()` requires at least one participant (`SessionNotStartableException`, 409).
- Participants register while in LOBBY, and mid-session only when `allowLateJoin` is set; a registration is rejected once the roster reaches `maxParticipants` (`SessionFullException`, 409) — a roster invariant the Session owns (added with orchestration, PR #2).
- FINISHED and ARCHIVED are immutable; archiving is terminal. Sessions are retained, never deleted (played history must stay reconstructable — same reasoning as quizzes).

`currentQuestionId` / `currentPhase` / `currentQuestionTimer` exist as the typed home for progression but are undriven in this PR — the gameplay PR advances them.

## Participant aggregate

`sessionId, identityReference (nullable) XOR guestParticipantToken (nullable), displayName, preferredLanguage (LanguageCode), lastSeenAt, totalScore (cached), state, answers (ParticipantAnswer collection), version.`

- **Exactly one identity mechanism** — a registered `IdentityReference` or a guest `GuestParticipantToken`, enforced in the constructor. Never a display name (not unique) or a connection (ephemeral).
- Lifecycle `JOINED → CONNECTED → DISCONNECTED → FINISHED`, with reconnection moving DISCONNECTED back to CONNECTED. A disconnect marks state and `lastSeenAt`; it never deletes the participant or resets score (ADR-003).
- **Connectivity is derived**, not stored: `isConnected()` returns `state == CONNECTED`, so it can never drift from the lifecycle.
- **Why the Participant owns its answers.** An answer has no life outside the participant who gave it, and a participant's `totalScore` is the cached SUM of its answers' `pointsAwarded` (ADR-003) — score and answers must stay consistent within one boundary. Modeling answers as their own aggregate would split that invariant across a transaction for no benefit. `recordAnswer` (model-only here) appends an answer and maintains the cached sum; the submission flow and point computation are later PRs.

## Value objects (all embeddable)

- **SessionPin** — exactly six digits. Format only; active-uniqueness and reuse-after-archive are a database concern (below).
- **SessionSettings** — `allowLateJoin, allowReconnect, showLiveLeaderboard, maxParticipants (1..1000)`. Execution settings only — never authoring settings (those are the Quiz's).
- **QuestionTimer** — `startedAt, durationSeconds, endsAt` (validates `endsAt = startedAt + duration`). A pure model: no scheduling, no expiry checking. Populated by progression later.
- **GuestParticipantToken** — an opaque 32-byte random secret the guest stores client-side to reconnect. A reconnection credential, never a business identity.
- **ParticipantAnswer** — `questionId, selectedOptionIds, answeredLanguage, submittedAt, responseTimeMillis, pointsAwarded`. Model only. `answeredLanguage` records which translation the participant actually played in — future analytics on which localizations get used. `selectedOptionIds` is folded to one column by an `AttributeConverter` (a nested collection cannot live in an element-collection embeddable).
- **ParticipantKey** — wraps an `IdentityReference` **xor** a `GuestParticipantToken`; value equality is what lets the Session enforce within-session uniqueness. Carries only *immutable* identity, so mirroring it into the roster can never diverge from the Participant.
- **SessionRosterEntry** — `{participantId, ParticipantKey, joinOrder}`, the roster element.

Plus **SessionPhase** `{QUESTION, REVEAL, LEADERBOARD}` — the gameplay loop inside IN_PROGRESS (PRD: Running → Reveal → Leaderboard → Running). Modeled only; no transitions this PR.

`LanguageCode` is reused from the quiz module (as `IdentityReference` is reused from identity) — see Future Work for promoting it to `common`.

## Uniqueness enforcement

- **Within-session identity/guest-token uniqueness** is enforced **inside the Session aggregate** via `ParticipantKey` equality on the roster — the aggregate genuinely owns its roster invariants (`ParticipantAlreadyJoinedException`, 409). Partial unique indexes on `session_participants` are the database backstop.
- **Global guest-token uniqueness** (the reconnection credential must be globally unique) cannot be seen by one Session, so — exactly like email uniqueness in RFC-002 — the **DB unique index on `participants.guest_token` is the authority**, translated by the future application service. Random 32-byte tokens make a collision astronomically unlikely; the constraint is correctness insurance.

This split — the aggregate enforces what it can see, the database is the authority for what crosses aggregates — is the established QuizChef pattern.

## Domain events

`SessionCreatedEvent`, `LobbyOpenedEvent`, `ParticipantJoinedEvent`, `ParticipantDisconnectedEvent`, `ParticipantReconnectedEvent`, `SessionStartedEvent`, `SessionFinishedEvent`. Ids + `occurredAt`, transport-free — the websocket module subscribes and translates them onto STOMP topics (RFC-005), never the reverse. The orchestration services (below) publish Created / LobbyOpened / ParticipantJoined / ParticipantReconnected / SessionStarted today; Disconnected and Finished arrive with connection management and gameplay.

**Planned: `SessionReadyEvent`.** A future event published when the host starts *and* all prerequisites are satisfied. Initially it would be equivalent to `SessionStartedEvent`, but it is the natural trigger for things that must happen once a session is truly ready to play — preloading question media, warming caches, notifying spectators, analytics, countdown initialization. Recorded here now so the orchestration vocabulary does not need revisiting when those capabilities land; not implemented in this milestone.

## Persistence — `V7__session_domain.sql`

`sessions` (flattened host reference, settings, nullable execution-pointer/timer columns; CHECKs for the enums, PIN shape, and `max_participants` range; a **partial unique index** on `session_pin WHERE state <> 'ARCHIVED'` for active-unique/reusable-after-archive), `session_participants` (the roster: participant id + flattened key + join order; PK, unique join order, partial unique indexes on identity and guest token per session), `participants` (nullable identity/guest columns with an exactly-one CHECK, globally unique guest token; no `connected` column — it is derived), `participant_answers` (PK participant+question). Additive on top of V6.

## Orchestration (implemented in PR #2)

The lobby flow is not CRUD — it is orchestration: a host creates a session, opens a lobby, participants join and reconnect, and the host starts. One application service per step, per ADR-005 the only place aggregates mutate, transactions open, and events publish. Each host operation takes `CurrentUser` and consults `AuthorizationService`; controllers only resolve `CurrentUser` and delegate.

```text
CreateSessionApplicationService.create(CurrentUser, CreateSessionCommand)      → authorize(QUIZ_HOST) + requirePublished(quiz) + unique PIN → SessionCreatedEvent
OpenLobbyApplicationService.openLobby(CurrentUser, pin)                         → authorize(QUIZ_HOST) + host  → LobbyOpenedEvent
JoinSessionApplicationService.join(CurrentUser, JoinSessionCommand)            → (anonymous-friendly) resolve PIN → Participant + roster entry → ParticipantJoinedEvent
ReconnectParticipantApplicationService.reconnect(CurrentUser, ReconnectCommand) → resolve by token or identity → connect → ParticipantReconnectedEvent, SessionSnapshot
StartSessionApplicationService.start(CurrentUser, sessionId)                    → authorize(QUIZ_HOST) + host  → SessionStartedEvent
SessionQueryService.summary(sessionId)                                         → (public read by id) → SessionSummary
```

**Realtime is automatic.** Services publish domain events; nothing calls `RealtimePublisher`. The RFC-005 projector subscribes and broadcasts `lobby.opened`, `participant.joined`, `participant.reconnected`, and `session.started` to the session topic. `SessionCreatedEvent` is not projected — no audience before anyone connects.

**PIN generation is a port.** `SessionCodeGenerator` (application) produces a *candidate*; `CreateSessionApplicationService` checks uniqueness among active sessions and retries, with the partial unique index as the final authority for the rare race. The generator is ignorant of persistence, so numeric PINs can become alphanumeric room codes, organization prefixes, or invitation codes without touching orchestration. `RandomSessionCodeGenerator` (infrastructure) is six secure-random digits — never a timestamp or a sequence (guessable).

**Authorization & the guest boundary.** Create / open-lobby / start require `QUIZ_HOST` *and* host ownership (holding the permission lets you host *your* sessions, not others'). Join, reconnect, and read are open — participants are anonymous-friendly and guests are first-class (ADR-003): an anonymous caller joins as a guest and is issued a reconnection token; an authenticated caller joins backed by their identity. Both flows are one code path. (`QUIZ_HOST` is held by `QUIZ_MASTER`/`ADMIN`; until login persists roles, tests mint host tokens directly, as elsewhere.)

**Cross-module boundary.** The quiz's "is this content runnable?" check goes through `quiz.application.QuizPublicationQuery.requirePublished(...)` — session depends on quiz's *application* layer, not its repository, keeping the boundary clean.

**Reconnection snapshot.** `reconnect` returns the RFC-005 replay contract, realized as the session module's own `SessionSnapshotView` (so session never depends on the websocket module — ADR-004). Generation is simple for now (in the lobby there is no question, timer, or score); `SessionRecoveryService` fills it out with gameplay.

**Still future:** `FinishSessionApplicationService`, inbound STOMP command handlers (delegating to these services), per-message authorization, and `SessionRecoveryService`.

---

# Alternatives Considered

**One aggregate (Session holds Participant entities)** — rejected: every answer submission would contend on the session root, and a participant's score/answers/connection have their own consistency needs. Two aggregates keep transactions small (the same reasoning that separates Question from Quiz).

**`connected` boolean on Participant** — rejected: redundant with the state machine and able to drift from it. Derived `isConnected()` cannot.

**`currentQuestionIndex` (positional)** — rejected: couples the session to the quiz's question ordering, which revisions could change. `currentQuestionId` points at exact content.

**`publishedQuizId`** — rejected: implies the latest mutable quiz. A session must denote the exact executed content; `publishedQuizVersionId` is revision-safe.

**Identifying participants by display name or connection** — rejected: names are not unique and connections are ephemeral (ADR-003/004). Exactly one of identity or guest token.

**Roster stores full participant state** — rejected: duplicates mutable state across two aggregates, risking divergence. The roster holds only the immutable `ParticipantKey`.

**Answer as its own aggregate** — rejected: an answer has no life outside its participant, and would split the cached-score invariant across a transaction.

---

# Risks

- Element collections rewrite on change (roster, answers); fine at session scale, revisit only if a hot path emerges during gameplay.
- `ParticipantAnswer.selectedOptionIds` is a converted CSV column — not queryable in SQL. Acceptable: answers are read through the aggregate, and analytics can normalise later if needed.
- PIN space is 10^6; at very high concurrent-session counts, generation retries grow. Fine for the church-scale target; revisit with a larger alphabet if needed.
- Timer/phase/current-question fields exist but are undriven — the gameplay PR must not mistake "modeled" for "working."

---

# Migration

`V7__session_domain.sql` is additive on top of V6; existing data is unaffected. Applied incrementally and validated by Hibernate against a real Postgres (Testcontainers).

---

# Open Questions

- **Session recovery ownership** — `SessionRecoveryService` in the session application layer vs. a transport-side concern. Leaning application layer (ADR-004 keeps transport dumb); decided with PR #1.5.
- **Single active connection policy** — enforced where the connection is known (transport), but the *decision* ("newer device wins") is domain. Where the invalidation event originates is a PR #1.5 question.
- **Host as participant** — may a host also play? Currently host and participants are distinct roster-wise; revisit if the product wants playing hosts.
- **Late-join mid-question** — does a late joiner get the current question or wait for the next? A gameplay-progression decision.

---

# Acceptance Criteria

- [x] Session and Participant modeled as two aggregates with explicit invariants; framework-independent (ArchUnit).
- [x] Session lifecycle and roster invariants (ordering, no duplicate, within-session key uniqueness, cannot-start-empty, finished/archived immutable) enforced by the aggregate with unit coverage.
- [x] Durable participants: XOR identity, derived connectivity, reconnection preserves score and answers (unit + integration).
- [x] Value objects (pin, settings, timer, guest token, answer, participant key) modeled and covered.
- [x] Domain events defined; transport-free.
- [x] `V7__session_domain.sql` applies incrementally on an existing (V6) database; Hibernate validates the mapping and a disconnected participant reloads intact (Testcontainers).
- [x] Orchestration (PR #2): the full lobby flow — create, open lobby, join (guest + registered), reconnect, start — over REST, with host authorization, anonymous-friendly joins, PIN generation via a port, and realtime events flowing automatically through RFC-005 (integration-tested end to end).
- [x] No gameplay, question progression, timers, answer submission, or scoring (out of scope).

---

# Future Work

- **Gameplay & scoring** (RFC-006): question progression, timers, answer submission, the scoring formula, leaderboards — and the reserved RFC-005 gameplay projections.
- **`FinishSessionApplicationService`**, inbound STOMP command handlers (delegating to the orchestration services), and per-message authorization at the transport.
- **`SessionRecoveryService`**: fill out the reconnection snapshot (current question, remaining time, submitted answer, leaderboard) once gameplay exists.
- **`SessionReadyEvent`** (see Domain events): published when a started session is truly ready to play — the trigger for media preloading, cache warming, spectator notification, analytics, countdown.
- **Single active connection policy**: joining from a new device invalidates the previous connection — belongs with connection management (transport).
- **Promote `LanguageCode` to `common`** — it is a domain-agnostic i18n primitive currently in the quiz module and now reused by session; a dedicated refactor PR should move it to `common` so neither consumer depends on the other's module for it.
- **`SessionCodeGenerator` → `RoomCodeGenerator`** — the port is already the right abstraction; if QuizChef grows live tournaments, breakout rooms, or practice rooms, a rename (and richer code formats) captures that without touching orchestration. No urgency.
- Session/quiz **revisions** (their own RFC) — `publishedQuizVersionId` is already shaped for them.
