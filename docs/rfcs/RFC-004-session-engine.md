# RFC-004 Session Engine

Status

Implemented

<!-- Draft | Proposed | Accepted | Implemented | Superseded by RFC-XXX
     Implemented — the session engine is feature-complete: the domain
     foundation (M4 PR #1), orchestration (PR #2), and the gameplay
     execution engine (PR #3) all ship. Scoring detail lives in its own
     RFC-006. Remaining items (inbound STOMP command handling, single active
     connection) are transport concerns tracked under Future Work / RFC-005
     and do not hold this RFC back. See README.md for the lifecycle. -->

Authors

Aman Minz

Created

2026-07-14

Updated

2026-07-18

---

# Summary

Defines the Session bounded context: the **Session** aggregate (a live run of a published quiz — PIN, host, lifecycle, execution settings, and an ordered roster) and the **Participant** aggregate (a durable player that survives disconnects), their state machines, value objects, domain events, and persistence.

The engine was built across three PRs: PR #1 the **domain foundation** (aggregates, value objects, events, persistence — no transport, no gameplay), PR #2 **orchestration** (the lobby flow over REST), and PR #3 the **gameplay execution engine** (the phase machine, host progression commands, the server-owned timer, answer submission, and the reconnection replay). Transport is specified separately in **[RFC-005](RFC-005-websocket-protocol.md)** and scoring in **[RFC-006](RFC-006-scoring-engine.md)**. This document is Implemented: it describes the session engine as it exists today. The genuinely-remaining pieces — inbound STOMP command handling and the single-active-connection policy — are transport concerns (RFC-005), listed under Future Work.

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
- **Scoring specifics** — the points formula, the speed bonus, the difficulty multiplier, and the leaderboard ordering live in **[RFC-006 Scoring Engine](RFC-006-scoring-engine.md)**. RFC-004 owns *when* an answer is accepted and *how* the engine progresses; RFC-006 owns *how many points* it earns and *how* scores rank. (Question progression, timers, and answer submission — originally deferred here — are now part of this engine; see *Gameplay execution*.)
- **Inbound STOMP command handling** — host commands enter over REST (below); a STOMP inbound path is a transport concern (RFC-005), Future Work.
- Authorization *implementation* — the application services consult `AuthorizationService`; the pattern is unchanged from PR #2.

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

`currentQuestionId` / `currentPhase` / `currentQuestionTimer` are the typed home for progression; the gameplay engine (below) drives them through the phase machine.

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

Plus **SessionPhase** `{QUESTION_OPEN, QUESTION_CLOSED, ANSWER_REVEALED, LEADERBOARD}` — the gameplay loop inside IN_PROGRESS (PRD: Running → Reveal → Leaderboard → Running). PR #1 modeled a placeholder set `{QUESTION, REVEAL, LEADERBOARD}`; PR #3 replaced it with the phases the engine actually transitions through, and `V8__gameplay_phases.sql` swaps the `current_phase` CHECK to match (no data migration — no session had been played). The transitions are driven; see *Gameplay execution*.

`LanguageCode` is reused from the quiz module (as `IdentityReference` is reused from identity) — see Future Work for promoting it to `common`.

## Uniqueness enforcement

- **Within-session identity/guest-token uniqueness** is enforced **inside the Session aggregate** via `ParticipantKey` equality on the roster — the aggregate genuinely owns its roster invariants (`ParticipantAlreadyJoinedException`, 409). Partial unique indexes on `session_participants` are the database backstop.
- **Global guest-token uniqueness** (the reconnection credential must be globally unique) cannot be seen by one Session, so — exactly like email uniqueness in RFC-002 — the **DB unique index on `participants.guest_token` is the authority**, translated by the future application service. Random 32-byte tokens make a collision astronomically unlikely; the constraint is correctness insurance.

This split — the aggregate enforces what it can see, the database is the authority for what crosses aggregates — is the established QuizChef pattern.

## Domain events

Lifecycle and lobby: `SessionCreatedEvent`, `LobbyOpenedEvent`, `ParticipantJoinedEvent`, `ParticipantDisconnectedEvent`, `ParticipantReconnectedEvent`, `SessionStartedEvent`, `SessionFinishedEvent`. Gameplay (PR #3): `QuestionStartedEvent` (carries the question id, the timer's `endsAt`, and its duration), `QuestionClosedEvent`, `AnswerSubmittedEvent` (session, participant, question — deliberately **no** score), `AnswerRevealedEvent` (question + correct option ids), `LeaderboardUpdatedEvent` (the ranked projection). All are ids + `occurredAt`, transport-free — the websocket module subscribes and translates them onto STOMP topics (RFC-005), never the reverse. Everything a session emits is now published by an application service (orchestration in PR #2, gameplay in PR #3); `SessionCreatedEvent` is the one event never projected — no audience exists before anyone connects. `ParticipantDisconnectedEvent` still awaits connection management (transport).

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

**Still future:** inbound STOMP command handlers (delegating to these services) and per-message authorization at the transport. Finishing a session and the reconnection snapshot are no longer future — they ship with gameplay (below): the advance command finishes the session when the quiz is exhausted, and `SessionSnapshotAssembler` fills out the replay.

## Gameplay execution (implemented in PR #3)

Live play is a phase machine inside `IN_PROGRESS`, and it is **server-authoritative end to end** (ADR-006): the server owns progression, timing, answer acceptance, correctness, and scoring; clients render state, submit commands, and receive projections. The phase loop:

```text
(IN_PROGRESS, no phase) → QUESTION_OPEN → QUESTION_CLOSED → ANSWER_REVEALED → LEADERBOARD → QUESTION_OPEN … → FINISHED
```

**Every transition is an aggregate method.** `Session.openQuestion` (from no-phase or LEADERBOARD), `closeQuestion`, `revealAnswer`, `showLeaderboard`, and `finish` each guard their precondition and throw `InvalidSessionTransitionException` (409) on an illegal move — a controller can never force a phase. `acceptsAnswersFor(questionId)` is the single gate answer acceptance passes through: true only while the session is `IN_PROGRESS`, the phase is `QUESTION_OPEN`, and the id matches the question in play.

**Host commands — one application service per step** (ADR-005), each taking `CurrentUser`, authorizing `QUIZ_HOST`, and asserting host ownership, exposed over REST by `GameplayController` under `/api/v1/sessions/{id}`:

```text
StartQuestionApplicationService.start(user, sessionId)      → open the first question           → QuestionStartedEvent
CloseQuestionApplicationService.close(user, sessionId)      → QUESTION_OPEN → QUESTION_CLOSED    → QuestionClosedEvent
RevealAnswerApplicationService.reveal(user, sessionId)      → QUESTION_CLOSED → ANSWER_REVEALED  → AnswerRevealedEvent (correct option ids)
ShowLeaderboardApplicationService.show(user, sessionId)     → ANSWER_REVEALED → LEADERBOARD       → LeaderboardUpdatedEvent (ranked)
AdvanceQuestionApplicationService.advance(user, sessionId)  → next question, or finish the session → QuestionStartedEvent | SessionFinishedEvent
```

**The engine owns question order; the host owns the pace.** `QuestionProgression` (a pure helper over the quiz's authored order) names the *next* question — the first when none has played, the successor of `currentQuestionId` otherwise, or none when the quiz is exhausted. `start` and `advance` decide *when* to move; they never pick *which*. When progression returns nothing, `advance` calls `Session.finish()` (clearing the phase, question, and timer) and publishes `SessionFinishedEvent`. Finishing is thus a natural consequence of advancing past the last question — no separate finish command.

**The server owns the timer** (ADR-006). `QuestionOpener` — the shared heart of start and advance — builds a `QuestionTimer` from the injected `Clock`, transitions the aggregate to `QUESTION_OPEN`, publishes `QuestionStartedEvent`, and arms the close through the `QuestionTimerScheduler` port. `SchedulingQuestionTimerScheduler` (infrastructure, a `ThreadPoolTaskScheduler`) fires at `endsAt` and calls `closeIfExpired`, which is **idempotent**: it closes only if `acceptsAnswersFor` still holds, so a manual host close or a moved-on game makes the fired timer a harmless no-op. The aggregate still owns the timer *state*; the scheduler only owns the wakeup. There is no polling loop and no clock in the domain — time enters solely through the shared `Clock`, so gameplay is deterministic and the tests never flake.

**Answer submission is where the server takes input and turns it into score** — `SubmitAnswerApplicationService`, open to participants (anonymous-friendly, no auth). Every guard the engine requires lives here or in the aggregate: the participant must exist and be **connected**, the question must be **open and the one in play** (`acceptsAnswersFor`), it must not be **already answered**, and the selected options must be **valid for the question** (empty selections and unknown options are rejected). A host cannot answer — a host has no participant. The server then stamps `responseTime` from the `Clock` (elapsed since the timer started, floored at zero), decides **correctness itself** by comparing the submitted option ids to the question's correct set (loaded via `quiz.application.GameplayQuizQuery` — the cross-module boundary stays on quiz's *application* layer), computes the points (RFC-006), and records a `ParticipantAnswer` that maintains the cached `totalScore` (ADR-003). **The acknowledgement carries no score** (`AnswerAcceptedView` is just participant + question); `AnswerSubmittedEvent` is projected to the submitting participant *only*, never the session — an opponent learns nothing from your answer.

**Scoring and the leaderboard** are the value half of the engine and live in their own **[RFC-006](RFC-006-scoring-engine.md)**: the framework-independent `ScoringService`/`ScoringPolicy` compute an answer's points, and `LeaderboardService` projects the standings (always computed, never stored). The gameplay services above call them; the formula and ranking rules are RFC-006's to state.

**Reconnection replay is now filled out.** `SessionSnapshotAssembler` (replacing the placeholder `SessionRecoveryService`) builds the RFC-005 replay contract — realized as the session module's own `SessionSnapshotView` so session never depends on the transport (ADR-004) — from the live aggregates: current phase and question, **remaining time on the clock** (derived from the shared `Clock`, clamped at zero), the participant's own **submitted options and score**, and the **leaderboard** projected fresh. Reconnecting mid-question therefore restores active gameplay immediately, not just lobby membership; in the lobby those gameplay fields are simply empty. Reconnection is idempotent — a refresh or second device that the server never saw drop reconnects cleanly (the Participant's `connect` rejects only the terminal FINISHED state).

**Beans, not framework in the domain.** `SessionGameplayConfiguration` wires `ScoringService`, `LeaderboardService`, the active `ScoringPolicy.classic()`, and the `gameplayTaskScheduler` as beans while the domain classes stay Spring-free — the established pattern.

## Participant-facing question content (frontend Phase 2 PR #4)

Gameplay execution above answers *"is this answer right, and what happens next"* — it never told a device *what a question actually says*. The frontend's live gameplay PR needed that, and none of the existing reads covered it: `GameplayQuizQuery` (the scoring boundary) is language-neutral and includes correctness, which a participant's device must never see before reveal; the session summary carries only the current question's *id*. `CurrentQuestionQueryService` (`GET /api/v1/sessions/{id}/questions/current`) fills that gap — the session module's **display** boundary, alongside `GameplayQuizQuery`'s **scoring** boundary, both reading the quiz module through its own application layer, never its repository.

It composes two application-layer reads and gates what they return by the session's own phase:

- **`quiz.application.GameplayQuestionContentQuery`** (new, quiz module): the question's prompt and options in every authored language, structurally incapable of carrying correctness — its return type (`PlayableQuestionContentView`) simply has no field for it, so there is no gate to forget. No ownership check: the caller is the session engine, which already knows its own session references this published quiz; re-deriving quiz ownership here would duplicate authority that belongs to `QuizPublicationQuery` at creation time.
- **`CurrentQuestionQueryService`** (session module): loads the session, requires `IN_PROGRESS` with a `currentQuestionId` (else 409 `session.no-current-question` — a legitimate "nothing to show yet" between questions or in the lobby, not a fault), fetches the content, and adds what only the session knows — position in the quiz, phase, and the server clock's remaining time (via the shared `Clock`, exactly like `SessionSnapshotAssembler`). **`correctOptionIds` is included only once the phase is `ANSWER_REVEALED` or `LEADERBOARD`** — the same moment `AnswerRevealedEvent` discloses it over the wire (ADR-006); before that the field is simply absent, never present-but-hidden.

Public like the session summary and join endpoints: a session is reached by its unguessable id, and the players it serves are anonymous guests by nature (`PublicEndpoints`). This is a plain REST read, not a protocol addition — RFC-005's wire vocabulary is unchanged; the frontend calls this endpoint once per relevant realtime event (question started/closed/revealed/leaderboard) rather than the event itself carrying content, keeping the private-ack-carries-no-score discipline in RFC-005 intact.

**The author's explanation joined the content view in frontend Phase 2 PR #5** (the reveal screen renders it), with the same reveal-time gating as correctness — an explanation routinely gives the answer away, so `CurrentQuestionQueryService` strips it (`PlayableLocalizationView.withoutExplanation`) until the phase is `ANSWER_REVEALED`/`LEADERBOARD`. The quiz module's view carries it unconditionally (content is the quiz module's to hand over); *when* it may cross the wire is the session's decision, in the same one place that gates `correctOptionIds`.

## The results read (frontend Phase 2 PR #5)

`ShowLeaderboardApplicationService` is a **command**: it moves the game to its leaderboard step and broadcasts the standings. That leaves two holes a results *UI* falls into: a refreshed client has no way to recover standings the broadcast already carried (re-issuing the command would 409 on its phase guard — and only the host could anyway), and after `FINISHED` there is no leaderboard step left to trigger at all. `GET /api/v1/sessions/{id}/results` (`SessionResultsQueryService`) is the read-side counterpart: the ranked entries projected fresh via `LeaderboardService` (never stored — ADR-006), plus the framing counts (`totalQuestions` via `GameplayQuizQuery`, `participantCount`), with **no transition and no event**. Interim (between questions) and final (after `FINISHED`) standings share the one shape, so clients render both with the same components.

Phase-gated for the same ADR-006 reason correctness is: standings during an open or merely closed question would reveal who answered correctly before the reveal does. Readable when the phase is `ANSWER_REVEALED` or `LEADERBOARD`, and always once `FINISHED`/`ARCHIVED`; otherwise 409 `session.results.not-available` — a state, not a fault. Under its own `/results` path deliberately: the whitelist patterns are method-agnostic, so sharing the host command's `/leaderboard` path would have opened the command's route to anonymous callers and left only the application-layer authorization guarding it — a distinct path keeps the command fully authenticated at both layers.

**Role-scoped visibility (Live Event UX, Phase 3).** The full standings — every name, score, and rank — are the *host's* projection: `GET /results` now requires the hosting identity (`QUIZ_HOST` + `SessionHostPolicy`), and was removed from `PublicEndpoints`. A participant device reads only its own row through `GET /api/v1/sessions/{id}/participants/{participantId}/result` (`SessionResultsQueryService.personalResult`): the ranking is still computed over the whole roster — a rank is meaningless otherwise — but a single entry leaves the projection, so no other participant's name, score, or rank can reach a participant device. Public with the same phase gating; the unguessable session and participant ids gate it, the same trust `POST /answers` already places in the participant id. For the same reason, the `leaderboard.updated` broadcast (which reaches every subscriber on the session topic) became a pure notification — its `entries` are always empty; clients never treated the payload as a data source (events notify, reads are authoritative). The host's projected lobby wall got the roster read this RFC's Future Work had flagged as missing: `GET /api/v1/sessions/{id}/participants` (`SessionRosterQueryService`, host-only) returns every joined participant's display name and connection state in stable join order — join events deliberately carry only ids.

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

`V7__session_domain.sql` is additive on top of V6; existing data is unaffected. `V8__gameplay_phases.sql` (PR #3) swaps the `sessions.current_phase` CHECK from the placeholder set to the real gameplay phases — no data migration, since no session had been played and `current_phase` was null everywhere. Both apply incrementally and are validated by Hibernate against a real Postgres (Testcontainers).

---

# Open Questions

- **Session recovery ownership** — *resolved*: recovery lives in the session application layer as `SessionSnapshotAssembler`, building the `SessionSnapshotView` the transport projects, so the session never depends on the websocket module (ADR-004) and the transport stays dumb.
- **Single active connection policy** — still open: enforced where the connection is known (transport), but the *decision* ("newer device wins") is domain. Where the invalidation event originates is a connection-management (RFC-005) question, still to build.
- **Host as participant** — may a host also play? Currently host and participants are distinct roster-wise; revisit if the product wants playing hosts.
- **Late-join mid-question** — a late joiner is admitted while `allowLateJoin` is set, and the reconnection snapshot already hands them the current phase, question, and remaining time, so they land in the live question; whether they *should* answer a question already underway (versus wait for the next) remains a product decision.

---

# Acceptance Criteria

- [x] Session and Participant modeled as two aggregates with explicit invariants; framework-independent (ArchUnit).
- [x] Session lifecycle and roster invariants (ordering, no duplicate, within-session key uniqueness, cannot-start-empty, finished/archived immutable) enforced by the aggregate with unit coverage.
- [x] Durable participants: XOR identity, derived connectivity, reconnection preserves score and answers (unit + integration).
- [x] Value objects (pin, settings, timer, guest token, answer, participant key) modeled and covered.
- [x] Domain events defined; transport-free.
- [x] `V7__session_domain.sql` applies incrementally on an existing (V6) database; Hibernate validates the mapping and a disconnected participant reloads intact (Testcontainers).
- [x] Orchestration (PR #2): the full lobby flow — create, open lobby, join (guest + registered), reconnect, start — over REST, with host authorization, anonymous-friendly joins, PIN generation via a port, and realtime events flowing automatically through RFC-005 (integration-tested end to end).
- [x] Gameplay execution (PR #3): the `QUESTION_OPEN → QUESTION_CLOSED → ANSWER_REVEALED → LEADERBOARD → …` phase machine enforced by the aggregate, with illegal transitions rejected; the five host commands over REST; the engine choosing question order while the host drives the pace; the session finishing when the quiz is exhausted (`V8__gameplay_phases.sql` aligning the phase CHECK). Unit + integration coverage.
- [x] The server-owned question timer arms from the shared `Clock` and closes an expired question idempotently against a manual host close (deterministic, no polling).
- [x] Answer submission validated entirely server-side (participant exists, connected, question open, not already answered, valid options, host cannot answer); the server stamps response time and decides correctness; the acknowledgement carries no score and is private to the submitter (integration-tested).
- [x] Reconnection restores active gameplay — current phase, question, remaining time, the participant's own answer and score, and the leaderboard (`SessionSnapshotAssembler`), verified by reconnecting mid-question in the full-game integration test.
- [x] Scoring and leaderboard delivered per **[RFC-006](RFC-006-scoring-engine.md)**; every OpenAPI path for the gameplay endpoints is documented (verified in the integration test).
- [x] Participant-facing question content (frontend Phase 2 PR #4): `GET /api/v1/sessions/{id}/questions/current` serves the question in play's prompt and options in every authored language, public and phase-gated — no correctness before `ANSWER_REVEALED`/`LEADERBOARD`, `409 session.no-current-question` between questions — verified end to end through every phase in the full-game integration test.
- [x] The results read (frontend Phase 2 PR #5): `GET /api/v1/sessions/{id}/results` serves the fresh-projected standings and framing counts, public, transition-free, and phase-gated (`ANSWER_REVEALED`/`LEADERBOARD`/`FINISHED`, else 409 `session.results.not-available`); the author's explanation joined the content read with reveal-time gating — both verified through every phase in the integration tests.

---

# Future Work

- **Inbound STOMP command handlers** (delegating to these application services) and per-message authorization at the transport — host commands enter over REST today; a realtime command channel is an RFC-005 concern.
- **`QuestionProgressChangedEvent`** — a single event emitted whenever the execution pointer advances (`fromQuestionId`, `toQuestionId`, `currentIndex`, `remainingQuestions`). Not needed for v1 — the phase events already carry enough — but recorded here as the clean seam for later analytics, spectator progress bars, audit, and recovery, so those don't each re-derive the current step. A policy-free addition when it lands.
- **`SessionReadyEvent`** (see Domain events): published when a started session is truly ready to play — the trigger for media preloading, cache warming, spectator notification, analytics, countdown.
- **Single active connection policy**: joining from a new device invalidates the previous connection — belongs with connection management (transport).
- **Promote `LanguageCode` to `common`** — it is a domain-agnostic i18n primitive currently in the quiz module and now reused by session; a dedicated refactor PR should move it to `common` so neither consumer depends on the other's module for it.
- **`SessionCodeGenerator` → `RoomCodeGenerator`** — the port is already the right abstraction; if QuizChef grows live tournaments, breakout rooms, or practice rooms, a rename (and richer code formats) captures that without touching orchestration. No urgency.
- Session/quiz **revisions** (their own RFC) — `publishedQuizVersionId` is already shaped for them.
