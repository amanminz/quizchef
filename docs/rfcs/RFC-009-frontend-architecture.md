# RFC-009 Frontend Architecture

Status

Implemented

<!-- Draft | Proposed | Accepted | Implemented | Superseded by RFC-XXX
     Implemented — the React platform foundation (Phase 2 PR #1) ships:
     routing, providers, auth, API layer, realtime client, state ownership,
     design tokens, shared components, and the test stack. Phase 2 PR #2
     (Quiz Authoring) is the platform's first feature, built on top without
     changing any of the PR #1 contracts — it established the features/
     module convention this RFC now documents. Phase 2 PR #3 (Host
     Dashboard & Session Creation) is the second feature module and the
     first realtime consumer; it too changed no PR #1 contract. Phase 2
     PR #4 (Live Gameplay Experience) is the third feature module — the
     one built around a client-side finite state machine and the one that
     finally answers RFC-009's own Open Question on guest identity. Phase 2
     PR #5 (Results, Leaderboards & Session Completion) extends that FSM —
     deliberately without redesigning it — to the full lifecycle, closing
     Phase 2: the complete author → publish → host → play → results
     workflow ships. Phase 3 PR #1 (Identity, Role Persistence & Host
     Onboarding) adds the fourth feature module (identity/) plus the
     registration page, making the whole journey — register → become a
     host → author → host → play — possible with no development-only
     shortcuts. See README.md for the lifecycle.
     Note: the Phase 2 spec named this "RFC-007", but RFC-007 is Media
     Storage and RFC-008 is Deployment; the next free number is 009. -->

Authors

Aman Minz

Created

2026-07-17

Updated

2026-07-18

---

# Summary

Defines the architecture of the QuizChef web frontend: a React 19 + TypeScript + Vite application in `frontend/` that is a **client of the platform, never part of it**. The backend is server-authoritative for everything that matters (ADR-006); the frontend renders state, submits commands, and receives projections. This RFC fixes the five contracts every future frontend PR builds on — the layering, the route table, **state ownership**, the API layer (with OpenAPI-generated types), and the realtime layer (one `RealtimeClient` over RFC-005) — plus the testing strategy that keeps them honest.

Phase 2 PR #1 implemented all of it as **infrastructure only**: no quiz authoring UI, no lobby, no gameplay. Phase 2 PR #2 (Quiz Authoring) is the first feature built on it, and in doing so establishes the **feature module** convention (`features/<name>/`) this RFC now documents alongside the original five contracts. Phase 2 PR #3 (Host Dashboard & Session Creation) is the second feature module and the first consumer of the realtime layer — its lobby realized the "live session projections" row of the state-ownership table. Phase 2 PR #4 (Live Gameplay Experience) is the third: it centralizes the gameplay phases into one finite state machine, keeps host and participant orchestration deliberately separate even where the UI looks the same, and resolves this RFC's own Open Question on where a guest's join identity lives. Phase 2 PR #5 (Results, Leaderboards & Session Completion) extends — never redesigns — that FSM to the full lifecycle (reveal, leaderboard, final results, session summary), and with it **Phase 2 is feature-complete**: author → publish → host → lobby → play → reveal → leaderboard → completion, end to end.

---

# Motivation

The backend stayed clean for eight PRs because its boundaries were written down before feature code existed. The frontend deserves the same treatment at the same moment — before the first feature page, not after five of them have each invented their own fetch calls, their own socket handling, and their own copy of the current user. The three failure modes this RFC exists to prevent: **state duplicated across stores** (and drifting), **transport details leaking into components** (axios/STOMP imports scattered everywhere), and **hand-maintained DTOs** quietly diverging from the API they mirror.

---

# Goals

- The backend's discipline, translated: components render, hooks coordinate, services communicate — business logic never lives in a component.
- One documented owner for every kind of state, enforced from day one.
- Request/response models **generated** from the backend's OpenAPI document, never written by hand.
- One realtime abstraction speaking RFC-005, with reconnect and resubscription handled once, below the feature code.
- A lightweight, dark-mode-ready design-token system — no heavyweight component library.
- A test stack (Vitest + Testing Library + MSW) that exercises the app through its real providers and routes.

---

# Non Goals

- Analytics, historical reports, player profiles, achievements, tournament mode, multi-session statistics, exports — Phase 3+ material; Phase 2 ends at a complete single-session lifecycle (PR #5).
- Question authoring (create/edit) — Phase 2 PR #2's question picker is read/search/compose only; question CRUD UI is a future PR (the API surface already exists, `questionApi.create`/`update`/`publish`/`archive`).
- Token refresh and server-side logout — the backend has neither (RFC-002 Future Work); see Authentication for what happens instead.
- Full accessibility audit, animations, media, church branding (deferred by the phase spec; the primitives are accessible-by-default where cheap: labeled fields, roles, focus rings, native `<dialog>`, keyboard-operable drag-and-drop).
- Offline support beyond an indicator; PWA concerns.

---

# Design

## Layering — the one rule

```text
Component → Hook → Service → Backend
```

Components never call `axios` or touch a STOMP session; they call hooks, hooks call services (`api/*` modules, `RealtimeClient`), and services speak to the backend. This is ADR-005's shape on the client: the "application service" layer is the hooks + services, and the components are as dumb as the backend's controllers. `RealtimeClient` is deliberately the axios of the socket world — a single abstraction below which the transport could become SSE or MQTT without a component changing (mirroring ADR-004).

## Project structure

```text
frontend/src/
  app/        App, Providers (stack + query retry policy), Router (route table)
  api/        axios instance, apiError mapping, identityApi / quizApi / questionApi / sessionApi
  auth/       AuthContext, AuthProvider, useAuth, useCurrentUser, RequireAuth, authStore
  realtime/   RealtimeClient, RealtimeContext, RealtimeProvider, useRealtime, SessionSubscriptions, connectionStore
  hooks/      cross-cutting hooks (useOnlineStatus)
  layouts/    PublicLayout, DashboardLayout, Breadcrumbs (route-aware, not generic — see below)
  pages/      one component per route
  components/ common/ forms/ feedback/ navigation/ — generic only, nothing feature-specific
  features/   <name>/ — hooks/, components/, queryKeys.ts (PR #2 established this; quizzes/, sessions/, gameplay/, identity/)
  theme/      tokens.css, uiPreferencesStore, useApplyTheme
  types/      api.gen.ts (generated), api.ts (aliases), protocol.ts (RFC-005, hand-written by design)
  utils/      cn, env, validation
  test/       setup, MSW server + handlers, testUtils, quizFixtures, sessionFixtures, gameplayFixtures, fakeStomp
```

**The `features/` convention (added Phase 2 PR #2).** `components/` stays generic by rule (RFC-009 already said "nothing quiz-specific"); a real feature needs its own hooks, presentational components, and query-key registry, and none of those belong in the cross-cutting `hooks/`/`components/` folders either. `features/<name>/` is where they live: `features/quizzes/hooks/` (query hooks + orchestration hooks), `features/quizzes/components/` (feature-specific presentational components), `features/quizzes/queryKeys.ts` (the one place a query's key and a mutation's invalidation target are defined, so they can't drift from each other by a typo). Lobby and gameplay will each get their own `features/<name>/` the same way.

## Routing

React Router v7, data router in the app (`createBrowserRouter`), one exported route table:

```text
/                          home                       public
/login                     sign in                    public
/register                  create an account          public
/play                      join a game                public (guests are first-class, ADR-003)
/dashboard                 role-adaptive home         RequireAuth
/profile                   own account & roles        RequireAuth
/profile/host-access       host onboarding            RequireAuth
/quizzes                   "My Quizzes"                RequireAuth
/quizzes/new               create a draft             RequireAuth
/quizzes/:quizId           edit metadata               RequireAuth
/quizzes/:quizId/questions compose (search + reorder)  RequireAuth
/quizzes/:quizId/review    read-only summary + publish RequireAuth
/sessions                  hosting dashboard           RequireAuth
/sessions/new              create a session            RequireAuth
/sessions/:sessionId       session details             RequireAuth
/sessions/:sessionId/lobby the host's lobby            RequireAuth
/sessions/:sessionId/play  the host's gameplay screen  RequireAuth
/play/:pin                 the participant's gameplay screen  public (guests are first-class, ADR-003)
/not-found + catch-all                                public
```

`/play` collects a PIN and joins; `/play/:pin` is where gameplay actually renders — a visitor arriving at `/play/:pin` directly (a shared link, or a refresh) who hasn't joined yet sees the same join form there instead of bouncing back to `/play`, since the PIN is already known from the URL.

`RequireAuth` is a layout route: unauthenticated visitors are redirected to `/login` carrying the path they wanted, and login returns them there. The route table is exported so tests mount the identical tree in a memory router. `/quizzes/new` is registered before `/quizzes/:quizId`, but the order is cosmetic — React Router (like the backend's own routing) resolves a literal segment over a variable one at the same level regardless of declaration order.

## State ownership

The load-bearing table. Each kind of state has exactly one owner, and the same datum never lives in two stores:

| State | Owner |
| --- | --- |
| Authentication (the JWT, session-expired flag) | Zustand (`authStore`, persisted) |
| Current route | React Router |
| Backend resources (current user, quizzes, sessions…) | TanStack Query |
| Realtime connection status | Zustand (`connectionStore`) |
| Live session and gameplay projections (session summary, current question, results/leaderboard) | TanStack Query, updated from realtime events |
| Transient lobby presence (the event-built roster) | Realtime only (`useParticipants` component state) |
| Hosted-session ids (which sessions this browser created) | Zustand (`hostedSessionsStore`, persisted) |
| Joined-session ids (which PINs this browser has played, per participant) | Zustand (`playerSessionStore`, persisted) |
| The gameplay phase itself (COUNTDOWN/QUESTION_OPEN/WAITING/…) | Derived, not stored — computed by `useGameplayState` from the session and question queries above |
| A question's in-progress answer selection (before submit) | React state (`AnswerGrid` — ephemeral until submitted) |
| UI preferences (theme) | Zustand (`uiPreferencesStore`, persisted) |
| Component-local UI (open modal, form input) | React state |

The proof of the rule: `useCurrentUser` is a TanStack Query over `/users/me`; the auth store holds **only the token**. Logging in stores a token and invalidates queries — it never copies the user into Zustand. The lobby (Phase 2 PR #3) realized the "projections" row: realtime events **invalidate or write into the query cache**, so server state keeps exactly one home even when it is pushed rather than fetched. Gameplay (Phase 2 PR #4) keeps growing that same row (the current-question query joins the session summary) rather than opening a new one. Two new stores stay narrow on purpose: `hostedSessionsStore` and `playerSessionStore` hold only **ids** (client-side facts — which sessions this browser hosts or has joined), never session or gameplay data itself; and the FSM `phase` is deliberately **not** a store at all — storing a derived value invites it to drift from the facts it's derived from, so `useGameplayState` recomputes it from the query cache on every render instead.

## Authentication

- **Login** stores the session-bound JWT (RFC-002) in the persisted auth store; a refresh keeps the login.
- **Expiry**: any non-auth 401 means the server-side identity session is gone (revoked or expired). The axios interceptor clears local auth and sets `sessionExpired`; `RequireAuth` routes to `/login`, which explains why. There is **no token refresh** because the backend issues no refresh tokens — recorded here so nobody "adds" one client-side.
- **Logout** is client-side token discard: the backend has no logout endpoint yet. Tokens are session-bound server-side, so revoking the identity session remains the server's kill switch. When RFC-002's logout lands, `AuthProvider.logout` gains one API call and nothing else changes.
- **Storage**: localStorage, versioned key. The XSS trade-off is accepted at this scale and mitigated by React's escaping and no third-party scripts; the session-bound design caps the blast radius of a stolen token.

## API layer

One axios instance (`api/axios.ts`) centralizes the base URL (same-origin by default; the dev server proxies `/api` and `/ws` to the backend), JWT injection from the auth store, a 10s timeout, and **error mapping**: every failure becomes an `ApiClientError` carrying the backend's stable `code`, the HTTP status, and field errors — callers switch on codes, never on transport artifacts. **Retries belong to TanStack Query alone** (Providers: 4xx never retried, network/5xx twice), so no request is ever retried at two layers.

**Generated types.** `OpenApiSpecExportTest` (backend) exports the live OpenAPI document to `backend/app/build/openapi.json`; `npm run generate:api` turns it into `types/api.gen.ts` (committed, so the frontend builds standalone). The api modules (`identityApi`, `quizApi`, `questionApi`, `sessionApi`) mirror the real endpoints exactly — no invented list endpoints, no hand-written DTOs. `quizApi` and `questionApi` were one module through PR #1 (nothing consumed the question calls yet); PR #2 split them along the same boundary the backend's own `QuizController`/`QuestionController` draw, once composition (`attachQuestion`/`detachQuestion`/`reorderQuestions` — quiz-side, since they act on the quiz's own composition) and search (`questionApi.search`) needed real homes. When the backend API changes, the pipeline makes the frontend fail to compile instead of silently drifting.

## Realtime layer

`RealtimeClient` wraps `@stomp/stompjs` and is the only file that knows STOMP exists:

- **connect / disconnect** are explicit. Nothing connects at app boot — realtime is only needed while a session is live. The lobby (PR #3) and now the gameplay screens (PR #4, `useGameplay`) each own the lifecycle for their own mount: connect when the screen appears, disconnect when it goes away.
- **subscribe(destination, handler) → unsubscribe**: handlers registered while disconnected activate on connect; every destination **resubscribes automatically after a reconnect**; the last handler leaving a destination unsubscribes it from the broker.
- **Automatic reconnect** (2s delay) and **heartbeats** (10s both ways) via STOMP config; connection state (`disconnected/connecting/connected/reconnecting`) mirrors into `connectionStore`.
- **Parsing**: frames become RFC-005 `ProtocolMessage`s; the protocol version is checked (a newer version logs a warning — RFC-005 bumps it only on breaking changes); unparseable frames are dropped without killing the subscription.
- The client connects to the raw-WebSocket transport of the backend's SockJS endpoint (`/ws/websocket`) — no SockJS client library needed; it can be added later purely inside this file if ancient-browser fallback ever matters.

`types/protocol.ts` hand-writes the RFC-005 envelope and payloads **by design**: the wire protocol is not in the OpenAPI document, and RFC-005 freezes it as a versioned contract — it changes deliberately, not per refactor. `SessionSubscriptions` mirrors the backend topic hierarchy (`/topic/session|participant|host/{id}`) — the only place destinations are built.

## Design tokens & theming

`theme/tokens.css` holds semantic HSL variables (surfaces, brand, support, lines, radius, elevation) consumed through the Tailwind theme, so components use `bg-background` / `text-muted-foreground` / `shadow-elevation-2`, never raw palette values. Tailwind's built-in scales are the spacing/typography tokens. **Dark mode** is a `data-theme` attribute on `<html>`: the persisted preference (`light/dark/system`) resolves against `prefers-color-scheme` and every component follows with zero per-component work. The bootstrap's Tailwind + shadcn-style conventions (CSS variables, `cn()`, cva) are kept — the token system is compatible with adding shadcn components later without adopting a component library now.

## Shared components

Generic only: `Button` (cva variants, loading state), `Card` family, `Modal` (native `<dialog>` — platform focus trap/ESC/top-layer), `ConfirmDialog`, `Spinner`, `LoadingOverlay`, `PageContainer`, `SectionHeader`, `EmptyState`, `ErrorPanel`, `FormField` (react-hook-form-ready), `ErrorBoundary`, `OfflineIndicator`, `AppNav` (gained an `orientation` prop for the sidebar, Phase 2 PR #2). Added for PR #2, still generic — reusable by any future multi-step workflow, not quiz-specific: `WorkflowHeader` (title, status slot, actions), `ProgressStepper` (step indicator), `EntityStatusBadge` (label + tone — callers map their own lifecycle to a tone, it knows no domain enum). `ConfirmActionDialog` from the original PR #2 task spec is not a new component: `ConfirmDialog` already covered it exactly, reused rather than duplicated. Nothing feature-specific exists in `components/` — feature components live in `features/<name>/components/`.

## Quiz authoring (Phase 2 PR #2)

The platform's first feature, and the template the next ones (lobby, gameplay) will copy.

**Orchestration hooks are the only coordination layer.** Per the layering rule, pages render and hooks coordinate — for a real feature, that split needs two hook tiers, not one: `features/quizzes/hooks/useQuizzes` / `useQuiz` / `usePublishQuiz` / `useArchiveQuiz` / `useQuestionLibrary` are thin TanStack Query wrappers (one query or mutation each); `useQuizAuthoring`, `useQuestionSelection`, `useQuizPublishing` sit above them and are what pages actually call — each owns one page's worth of workflow (create+edit metadata; compose questions; review+publish) and returns exactly the data and actions that page renders. A page component never calls `useMutation` directly.

**Optimistic updates are scoped to what's naturally reversible.** Attach, detach, and reorder (`useQuestionSelection`) update the cached quiz composition immediately via TanStack Query's `onMutate`/`onError`/`onSettled` and roll back to the last known server state if the request fails; `onSettled` always refetches regardless of outcome, so the client reconciles to the server's truth even after a successful mutation (ADR-006 spirit: optimistic UI is a rendering convenience, never a source of truth). Create, publish, and archive (`useQuizAuthoring`, `usePublishQuiz`, `useArchiveQuiz`) are lifecycle transitions, not composition edits — they stay server-confirmed with no optimistic UI, so a user is never shown a "published" state that a 409 then contradicts.

**Client-side validation previews the server's own rule, never replaces it.** `useQuizPublishing` computes a "not ready to publish" warning by checking the same condition `PublishQuizApplicationService` enforces server-side (every attached question must carry the quiz's default language, RFC-003) — reading it from the already-fetched question summaries, entirely client-side. It is a UX preview, not a gate: the Publish button disables on the obvious case (no questions), but the request still goes to the server and a 409 is handled like any other mutation error, never assumed away.

**Drag-and-drop reordering** (`/quizzes/:quizId/questions`) uses `@dnd-kit/core` + `@dnd-kit/sortable` — a new dependency, added because the workflow spec requires real drag-and-drop with keyboard support and nothing already in the stack covers it. `dnd-kit`'s `KeyboardSensor` (alongside `PointerSensor`) gives keyboard operability for free rather than as an afterthought. The draggable wrapper (`SortableQuestionRow`) is kept separate from the presentational `QuestionRow` — the row itself stays DnD-library-agnostic.

**Breadcrumbs are route-aware, so they don't live in `components/navigation/`.** `layouts/Breadcrumbs.tsx` resolves a quiz's title (via the same `useQuiz` query the page uses — same cache, no extra request) to render it in the trail; PR #3 taught it session PINs the same way. `components/navigation/AppNav`, by contrast, stays a static, feature-ignorant link list. The distinction: `layouts/` may know about app structure and features; `components/` never does.

## Session hosting (Phase 2 PR #3)

The second feature module (`features/sessions/`) and the platform's first realtime consumer. The workflow: published quiz → create session → review details → open lobby → start. It integrates **only the endpoints the backend has** — where the phase spec asked for more, the UI adapts to the contract rather than inventing one:

- **No session list endpoint exists**, so the dashboard is a local registry: `hostedSessionsStore` (Zustand, persisted) holds the **ids** of sessions this browser created, and `useSessions` hydrates each through its own `GET /sessions/{id}` detail query. Only ids live in the store — session data stays in Query, keeping the ownership table honest. Ids the server no longer knows (404 — sessions expire from the Redis-backed store) prune themselves. When a backend list endpoint lands, the store is deleted and `useSessions` becomes one query; nothing else changes.
- **`CreateSessionRequest` carries only `publishedQuizVersionId`** (the quiz's own id today — `QuizPublicationQuery`), so "configure a session" is honest review, not input: `ConfigurationSection` renders the server-assigned settings read-only, and the create page offers exactly one choice — which published quiz to run. Validation is server-authoritative; a quiz unpublished between listing and create surfaces as the 409 it is.
- **There is no cancel endpoint**, so there is no cancel button — unstarted sessions age out server-side. Documented, not faked.

**The lobby is a realtime application with one division of truth.** The session summary query establishes initial state and remains the single home of server state; from then on STOMP events are the change feed. `useLobby` (the orchestration hook, the lobby page's only call) subscribes through `SessionSubscriptions` — pages never construct destinations — and answers every lifecycle or roster event by **invalidating the summary query**: the push says *that* something changed, the refetch says *what* it now is (ADR-006's spirit — the server stays the authority even over its own notifications). Nothing polls; between events the summary is simply never refetched.

**Presence is the one thing realtime owns outright.** `useParticipants` builds the roster purely from `participant.joined/disconnected/reconnected` events — transient by design: it exists only while the lobby is mounted. The events carry only participant ids (RFC-005 keeps roster churn cheap), so entries render as deterministic id-derived avatars; the server's `participantCount` stays the headline number, and participants who joined before the view subscribed appear as an explicit "joined before this view opened" row rather than as fabricated entries. Disconnects dim an entry, never remove it (ADR-003 durable participants).

**Start is server-confirmed with no optimistic transition.** The button enables when the server-reported summary says startable (in LOBBY with ≥ 1 participant — the same precondition `StartSessionApplicationService` enforces, previewed as UX exactly like `useQuizPublishing` does); the page navigates to the gameplay route only when the cached state says `IN_PROGRESS`, whether that truth arrived as the start response or as a `session.started` broadcast — one navigation trigger, driven by server state. The gameplay route itself is a deliberate placeholder: this PR ends at "the host successfully launches a live session"; question presentation, timers, and the leaderboard are PR #4.

**The lobby owns the realtime connection lifecycle for now** — connect on mount, disconnect on unmount; a dropped connection shows a banner while `RealtimeClient` reconnects and resubscribes below the feature code. When gameplay lands, connection ownership moves up to span lobby → gameplay. Host and participant workflows will share presentational components but never orchestration hooks: `useLobby`/`useHostControls` are host-only by design — the participant's `/play` experience gets its own hooks in the gameplay PR.

## Live gameplay (Phase 2 PR #4)

The third feature module (`features/gameplay/`), and the one the PR's own guiding principle names outright: **gameplay is driven by the backend; the frontend never advances the game; realtime events are notifications.** Every screen — host and participant alike — renders off one thing.

**One finite state machine, not scattered conditionals.** `useGameplayState` is the single place that looks at both the session summary (`state`, `currentQuestionId`) and the current-question read (`phase`) and derives one `GameplayPhase`: `LOBBY | COUNTDOWN | QUESTION_OPEN | WAITING | FINISHED`. Every gameplay component renders off this one value — no component re-infers "are we between questions?" from its own reading of session/question fields. `WAITING` deliberately collapses the backend's `QUESTION_CLOSED` / `ANSWER_REVEALED` / `LEADERBOARD` phases into one client phase, because this PR renders no reveal or leaderboard content (Non Goals) — the participant only needs "the question is no longer open," and the host's own phase-specific logic (below) lives in `useGameHost`, not in the FSM itself.

**A new backend read makes participant devices possible at all.** Nothing before this PR let an anonymous participant learn a question's actual content — `GameplayQuizQuery` (scoring) carries correctness and is never exposed to a client; the session summary carries only the question's *id*. The session module gained `GET /api/v1/sessions/{id}/questions/current` for exactly this (RFC-004's new "Participant-facing question content" section): public, phase-gated (`correctOptionIds` appears only once revealed, mirroring `answer.revealed`), 409 `session.no-current-question` between questions treated as "nothing to show yet" rather than an error (`useCurrentQuestion` doesn't retry it, and `useGameplayState` swallows it rather than surfacing `questionError`). This is a plain REST addition, not a protocol change — RFC-005's wire vocabulary is untouched; the frontend still calls this endpoint on its own after a relevant event, never expects content riding the event itself.

**`useGameplaySubscriptions` is gameplay's `SessionSubscriptions`-consumer, not a new destination-builder.** It composes the existing `sessionTopic`/`participantTopic` helpers (RFC-009's "only place destinations are built" rule is unchanged) into one hook that wires the session-wide broadcast and, for a participant, their private topic, to a single dispatcher. `useGameplay` — shared by both host and participant — subscribes through it, and on every session-lifecycle or question-progression event invalidates the session summary and/or current-question query (never both blindly; the two event sets are distinguished) so a push only ever tells the UI *that* something changed, exactly like the lobby's realtime pattern.

**Host and participant orchestration never share a hook, even though `useGameplay` is shared underneath.** `useGameHost` wraps it with the one host action, `nextStep`, which sequences whichever of `startQuestion`/`revealAnswer`/`showLeaderboard`/`advanceQuestion` the *current, already-known* phase requires — including `showLeaderboard`, which is called but never rendered, because `Session.openQuestion` (RFC-004) accepts only "no phase yet" or `LEADERBOARD`, so the domain's own state machine requires that step even though this PR shows no leaderboard UI. `usePlayerGameplay` wraps the same `useGameplay` with a join step and answer submission the host never has. Sharing `QuestionCard`/`QuestionTimer`/`AnswerGrid` (the host renders `AnswerGrid` `readOnly`, purely to see what participants see) costs nothing; sharing the hook would blur two genuinely different permission surfaces.

**Reconnect strategy — resolves this RFC's own Open Question.** A guest's join identity (`participantId` + `guestParticipantToken`) is kept in `playerSessionStore` (Zustand, persisted, keyed by session **PIN** — the identifier a participant actually knows), mirroring `hostedSessionsStore`'s "ids only" discipline exactly. Every mount that finds a stored record calls `POST /sessions/reconnect` (RFC-005's replay contract) before rendering live content — not just on a literal page refresh: the very first moment after joining is treated the same way, because `join` alone never marks a participant connected (`Participant.connect` only runs inside reconnect — `SubmitAnswerApplicationService` would otherwise 409 `session.participant.not-connected` on every first answer). The same call re-runs whenever the realtime connection recovers after dropping, so a flaky connection reconciles to the snapshot rather than trusting whatever events it might have missed. The snapshot's `submittedOptionIds` seeds `useAnswerSubmission`'s per-question submitted map, which is how a mid-question refresh shows `SubmissionStatus` immediately instead of a blank grid.

**Duplicate submission is prevented locally and reconciled against the server.** `useAnswerSubmission` guards `submit` before it ever leaves the browser (`hasSubmitted`, keyed by `questionId` — a new question always starts unguarded, no explicit reset needed), and treats the server's own `session.answer.not-accepted` 409 (already answered, or the question closed) as confirmation rather than failure — functionally, both mean the same thing to the participant. Every other error is a real submission failure and surfaces as one.

**The timer disables input; it never decides closure.** `useCountdown` ticks locally against the server's `endsAt` (never a client-invented duration), and `AnswerGrid` disables once it reports expired — a UX convenience that can run a beat ahead of the real `question.closed` event over the wire. The **actual** phase transition still comes only from that event (through `useGameplayState`), so an answer submitted in the gap is still evaluated by the server's own clock, never assumed accepted or rejected by the client.

**No fabricated countdown duration.** Between `session.started` and the first `question.started`, there is no server-issued countdown setting to count down from (`SessionSettingsDto` has none) — `CountdownOverlay` is an honest indeterminate "get ready" animation, not a fake number ticking to zero, and it disappears the instant the real event arrives.

## Results & session completion (Phase 2 PR #5)

The last stretch of the lifecycle, built by **extending** PR #4's machinery, never replacing it — the PR's own constraint, honored literally: `useGameplayState` gained three phases, `useGameplaySubscriptions` gained zero subscriptions, and no new route exists.

**The FSM, final form.** `LOBBY → COUNTDOWN → QUESTION_OPEN → WAITING → ANSWER_REVEALED → LEADERBOARD → (next question…) → FINISHED`. PR #4's `WAITING` had collapsed everything after the close because nothing later rendered; PR #5 narrows it to exactly the backend's `QUESTION_CLOSED` and gives `ANSWER_REVEALED`/`LEADERBOARD`/`FINISHED` their own rendered states. Consequently the host's `nextStep` un-collapsed too: what was one click silently chaining reveal→leaderboard→advance is now three visible, separately-labeled server commands (**Reveal Answer → Show Leaderboard → Next Question**, or **Finish Quiz** on the last one — the same advance command either way; the *server* decides that advancing past the last question finishes the session, the label only reflects what the already-fetched question numbers say will happen). The called-but-never-rendered `showLeaderboard` wart PR #4's risks flagged is gone — the step now renders.

**Leaderboard ownership: one read, never computed client-side.** The backend gained `GET /sessions/{id}/results` (RFC-004's "results read" section) because the standings previously existed only as a host-only phase-transitioning *command* and a broadcast — neither survives a refresh, and neither exists after FINISHED. `useResults` is the one hook over that read (no separate `useLeaderboard` — interim and final standings are the same backend state, and two cache entries for it would violate the ownership table); every ranking, score, and the winner render the server's rows verbatim, in order. `answer.revealed`, `leaderboard.updated`, and `session.finished` invalidate it — the same event-then-refetch pattern as everything else; the broadcast's own payload is deliberately ignored as a data source now that a read exists to reconcile against. The one client-side touch: `LeaderboardTable` diffs the *previous server snapshot it rendered* (a ref) to show score deltas and rank movement — presentation of two server states, absent on the first render after a refresh because there is nothing truthful to diff against.

**The reveal renders three server facts, computes none.** Correct options come from the phase-gated `correctOptionIds`; the author's explanation now rides the same content read (added backend-side this PR, stripped until reveal — it routinely gives the answer away); and the participant's own verdict is `verdictFor(submitted, correct)` — set-equality of two server facts, the very comparison `SubmitAnswerApplicationService` scores by, because **no per-answer verdict field exists on the wire** (a documented contract adaptation, not a client-side scoring rule; the helper deletes itself the day the backend exposes one).

**Completion workflow.** `FINISHED` renders from the summary + results reads alone — which is precisely what makes refresh-during-results recovery trivial for both roles: a fresh mount needs no event history. Host: winner, podium, final standings, statistics, session summary, then **Host Another Session** / **Return to Dashboard**. Participant: podium, own rank and score (their row in the server's standings — a lookup, not a computation), final standings with their row highlighted, **Play Another Quiz** (back to the PIN entry). Presentation components are shared across the two roles per the spec; only the orchestration hooks stay separate, as they have since PR #4. Session duration and completion timestamps are **not** shown: the session summary carries no started/finished times (contract adaptation — noted, not approximated client-side).

## Identity & host onboarding (Phase 3 PR #1)

The fourth feature module (`features/identity/`), and the one that retires the last development-only shortcut: a fresh user registers (`/register` — the page this RFC's Future Work had reserved for "the first onboarding-focused PR"), lands as a member, and becomes a host with one server-confirmed click.

**One query, many projections — roles never get a second home.** `useProfile`, `useRoles`, and `usePermissions` are all projections over the *same* shared `currentUser` query (`currentUserQueryKey`, now exported from `auth/useCurrentUser`); `useHostAccess` is the one mutation, and its `onSuccess` invalidates that one key — at which point every role-aware surface (navigation, dashboard, the onboarding page itself) re-renders from the server's new truth. No token swap is needed: the backend authorizes from persisted roles, so the same JWT hosts immediately (RFC-002).

**Frontend authorization is cosmetic — stated as a rule, enforced by shape.** `usePermissions` decides what to *show*: the dashboard's member view versus host view, which nav links render, the `PermissionBanner`/`UnauthorizedState` guidance. It never decides what is *allowed*: no route is hidden (a member navigating to `/quizzes` gets whatever the backend says, and 403s render as real outcomes with a path forward), and while the query loads everything reads as not-granted, so screens briefly show their least-privileged form rather than flashing capabilities that may vanish.

**Navigation rules.** `AppNav` stays feature-ignorant (it gained a `links` prop, nothing else); `DashboardLayout` — a layout, allowed to know features — computes the links from permissions: Dashboard and Profile always; Quizzes with `QUIZ_CREATE`; Sessions with `QUIZ_HOST`. Navigation reflects permissions; routes stay reachable; the backend still decides.

**Onboarding is server-confirmed like every lifecycle transition.** The success state (`PromotionSuccess`) renders only from the server's verdict — either the mutation's `GRANTED` response or, for a returning host, the roles already in the query. `HostAccessPage` therefore needs no local "did I just promote?" state at all: `isHost` from the shared query *is* the onboarding status.

## Forms & validation

react-hook-form + zod via a `zodForm(schema)` helper; shared field schemas (`emailSchema`, `passwordSchema` 8–128, `sessionPinSchema` 6 digits, `displayNameSchema`) mirror the backend's rules for faster feedback — **the server remains the authority** (ADR-006 spirit: client validation is UX, not truth).

## Error handling

Four layers, each with one job: the **axios interceptor** normalizes every API failure to `ApiClientError`; **TanStack Query** surfaces `isError`/`error` to pages, which render `ErrorPanel` with retry; the global **ErrorBoundary** catches render-time throws; **OfflineIndicator** shows loss of connectivity. Unauthorized (401) is handled once, in the interceptor, as session expiry.

## Testing strategy

Vitest + Testing Library + MSW (network mocked at the boundary — the app's real axios stack runs). `renderApp()` mounts the **real route table inside the real provider stack** in a memory router, so tests exercise routing, auth, queries, and interceptors together; MSW's `onUnhandledRequest: "error"` fails any test that talks to an unmocked endpoint. `RealtimeClient` is tested against a scriptable fake STOMP client injected through its factory seam (connect/reconnect/resubscribe/dispatch/unsubscribe). One deliberate wrinkle: tests mount the route tree through the declarative `MemoryRouter` because the data router's Request machinery clashes with MSW under jsdom — same tree, same guards, no lost coverage.

**104 tests** (95 through Phase 2 + 9 from Phase 3 PR #1's identity work — the numbers below record each PR's contribution: 23 from PR #1 + 28 from PR #2's authoring workflow + 24 from PR #3's hosting workflow + 14 from PR #4's gameplay experience + 6 net new from PR #5's results, with several PR #4 tests rewritten for the un-collapsed phases): routing/auth/API-client/realtime/error-boundary from PR #1, plus every authoring page (list lifecycle-sectioning and its own empty/error states, create validation and error surfacing, metadata load/save/version-conflict, question search/attach/detach, publish/archive behind confirmation with its precondition warning) and every new route's auth redirect. PR #3 covers the hosting workflow end to end: the dashboard's lifecycle sectioning, empty state, and 404 pruning; create-session (published-only listing, metadata panel, the server-confirmed create, the 409 surface); session details with open-lobby and its authorization failure; and the lobby against the fake STOMP transport — roster join/disconnect events with the event-then-reconcile refetch, the pre-subscription count row, the reconnect banner, the start flow's server confirmation, its 403 rejection, and the remote `session.started` navigation. The fake transport moved from `RealtimeClient.test.ts` into `test/fakeStomp.ts` once the lobby tests needed it, and `renderApp` gained an injection seam for a fake-backed `RealtimeClient`. **`useQuestionSelection`'s reorder is tested at the hook level** (`renderHook`, a scriptable MSW handler with an artificial delay to make the optimistic window observable) rather than by simulating a drag gesture in jsdom — dnd-kit's pointer events don't translate meaningfully to jsdom's synthetic event system, and the actual logic under test (optimistic update → rollback on failure) lives in the hook, not in dnd-kit's own gesture handling (which the library's own tests already cover).

PR #4 covers both gameplay screens against `gameplayFixtures` and the same fake STOMP transport: the participant screen's PIN join and landing on the question, submitting an answer and being unable to submit twice, recovering an already-submitted answer purely from the reconnect snapshot after a simulated refresh, a remote `question.closed` → `question.started` pair moving the FSM through `WAITING` and into the next question with no local action, the connection-lost banner and its recovery, and a stale stored participant record clearing itself and falling back to the join form on `session.participant.not-found`; the host screen's read-only monitoring view, starting the first question from `COUNTDOWN`, a 403 surfacing without leaving the page, and a remote `question.closed` updating the host with no host action. **The timeout scenario needed no fake timers**: the fixture simply hands the question an `endsAt` already in the past, so `useCountdown`'s very first synchronous computation reports it expired — deterministic, and avoids the well-known fragility of combining `vi.useFakeTimers()` with Testing Library's `waitFor` polling.

PR #5 covers the results stretch: the host stepping through **Reveal Answer → Show Leaderboard → Next Question** with exactly one server command per click (call order asserted), the reveal rendering the server's correct option and explanation, the **Finish Quiz** label on the last question, and the full final-results screen recovered on a fresh mount with no event history (the host refresh-recovery case the new results read exists for); the participant's correct and incorrect verdicts (seeded purely from the reconnect snapshot's `submittedOptionIds` against the revealed `correctOptionIds`), the leaderboard with their own row highlighted and position line, a `leaderboard.updated` broadcast refetching new standings, and the completion screen — podium, own rank, Play Another Quiz — likewise recovered from a fresh mount.

Phase 3 PR #1 covers identity: registration (register → auto-login → the member dashboard; the duplicate-email 409 surfaced verbatim), the member-versus-host dashboard and navigation (a plain member sees Become a Host and no authoring/hosting links; the default test identity remains a host so every earlier suite runs unchanged), the profile page's roles and hosting path, the promotion flow (a stateful `/users/me` handler flips on the grant, and the test asserts the *navigation* gains Quizzes/Sessions — proving the one-query invalidation reaches every role-aware surface), the already-a-host visit showing success instead of the button, and a denied promotion surfacing without navigation.

**jsdom gap found and fixed once, for every future test:** jsdom implements no `<dialog>` imperative API (`showModal`/`close`) — PR #1 built `Modal`/`ConfirmDialog` on native `<dialog>` but no PR #1 test ever opened one. PR #2's confirmation flows were the first to exercise that path, so the polyfill was added to the shared `test/setup.ts` (same pattern as the pre-existing `matchMedia` stand-in) rather than worked around per test.

---

# Alternatives Considered

**Hand-written DTO interfaces** — rejected: they drift silently. Generated types turn API drift into compile errors (Aman's recommendation #1).

**Components using STOMP directly / a subscription hook per feature** — rejected: transport details would spread through feature code and every feature would re-solve reconnection. One `RealtimeClient` mirrors the backend's ADR-004 seam (Aman's recommendation #2).

**Everything in Zustand / everything in Query / Redux** — rejected: server state in a client store must be manually invalidated (and won't be); client state in Query gains nothing. The ownership table (Aman's recommendation #3) gives each kind of state the tool built for it. Redux adds ceremony without adding an owner.

**A component library (MUI, Mantine, full shadcn/ui)** — rejected for now: the phase spec asks for a lightweight system; tokens + Tailwind + a dozen primitives cover the platform. The shadcn-compatible conventions keep that door open.

**Token in memory only (no persistence)** — rejected: every refresh would log the host out mid-session prep. localStorage with a versioned key is the pragmatic church-scale choice; the trade-off is documented under Authentication.

**SockJS client with fallback transports** — not needed: modern browsers all speak WebSocket; the raw-WebSocket transport of the existing SockJS endpoint works today, and a fallback would be an implementation detail of `RealtimeClient` if ever required.

**Auto-connecting realtime at app boot** — rejected: a visitor reading the home page holds no session; connections are opened by the features that need them.

**Optimistic UI for every mutation, including publish/archive** — rejected: those are lifecycle transitions with real business significance (RFC-003), not reversible edits. Showing "published" before the server confirms it risks a visible flicker back to "draft" on a 409, which is a worse experience than a brief, honest loading state.

**A native HTML5 drag-and-drop API (`draggable` + drag events) instead of a library** — rejected: no built-in keyboard operability, inconsistent touch support, and a materially worse API for sortable-list reordering specifically. `@dnd-kit` costs one dependency and buys correct behavior across input modes for free.

**Feature-specific hooks/components inside the existing `hooks/`/`components/` folders** — rejected: RFC-009 already draws that line ("generic only, nothing feature-specific") for a reason — a folder that mixes generic and feature-specific code stops being a safe place to add something reusable without checking it isn't accidentally quiz-aware. `features/<name>/` gives feature code an unambiguous home.

**A numeric client-side countdown for the pre-question `COUNTDOWN` phase** — rejected: no backend setting names a duration to count down from; inventing one would be the frontend deciding a game-timing detail the server never sanctioned. `CountdownOverlay` stays an honest indeterminate animation that ends the instant `question.started` actually arrives.

**Rendering the reveal/leaderboard phases even though this PR's scope excludes leaderboard UI** — rejected: `useGameplayState` collapses `QUESTION_CLOSED`/`ANSWER_REVEALED`/`LEADERBOARD` into one `WAITING` phase instead. The backend's state machine still requires passing through `LEADERBOARD` before the next question can open (`Session.openQuestion`), so `useGameHost.nextStep` still calls `showLeaderboard` — it is simply never rendered, which is different from being skipped.

**A single shared `useGameplayOrchestration` hook for host and participant** — rejected, mirroring PR #3's host/participant split: `useGameHost` and `usePlayerGameplay` share `useGameplay` underneath (the FSM, subscriptions, and reconnect-triggered refetch) but never share orchestration above that, because their commands and permissions genuinely differ — a host that could accidentally call `submitAnswer` or a participant that could accidentally call `advanceQuestion` would be a real authorization bug waiting to happen, not just a stylistic one.

**Fake timers for the answer-timeout test** — rejected: `vi.useFakeTimers()` combined with Testing Library's `waitFor` (which itself polls on a real or faked timer) is a known source of flaky, hard-to-debug test deadlocks. Handing the fixture an already-past `endsAt` gets the same assertion deterministically, because `useCountdown` computes its first `remainingMillis` synchronously from `Date.now()` vs. the target with no need to advance anything.

---

# Risks

- **The generated-types pipeline needs the backend build** (`OpenApiSpecExportTest` → `generate:api`). Mitigated: `api.gen.ts` is committed, so the frontend builds without the backend; regeneration is a documented two-command step. CI wiring for drift detection is future work.
- **localStorage JWT** is readable by successful XSS. Accepted and documented; session-bound tokens cap the damage, and no third-party scripts run.
- **Single bundle (~964 kB min after PR #5, up from ~951 kB after PR #4)** — still fine for church scale; with Phase 2 now feature-complete, code splitting is squarely Phase 3 (Product Hardening) material.
- **The hosted-session registry is per-browser.** A host who switches devices (or clears storage) loses the dashboard's memory of their sessions — the sessions themselves live on, reachable by id/PIN. This is the honest cost of not inventing a list endpoint; it disappears the day the backend grows one.
- **The lobby roster shows ids, not names.** RFC-005's roster events deliberately carry only the participant id; until a roster read model or richer join event exists, the lobby renders id-derived avatars and the count row. Gameplay's own screens sidestep this (the host sees no roster, only a count; the participant sees no roster at all), but the leaderboard PR (#5) will need real display names somewhere — the join request already carries one, only nothing surfaces it back to other participants yet.
- **The joined-session registry is per-browser, same shape as the hosted one.** A participant who switches devices mid-session cannot resume through `playerSessionStore` — they would need to rejoin (a fresh `Participant`, not a reconnect to the old one). Acceptable at church scale for now; a future "resume by name" or account-linked join would remove this.
- **~~The host's `nextStep` calls `showLeaderboard` with nothing ever rendering it~~** — *resolved in PR #5*: the leaderboard step now renders, and `nextStep` issues one visible command per phase.
- **The verdict helper duplicates the server's scoring comparison.** `verdictFor` renders set-equality of the participant's accepted submission against the revealed correct set — the same rule `SubmitAnswerApplicationService` scores by — because no per-answer verdict crosses the wire. If the backend's rule ever changed (partial credit, ordering), this display would silently diverge; the helper's comment and this line exist so the backend change knows to delete it in favor of a wire field.
- **jsdom test wrinkle**: tests use the declarative router (data-router Requests clash with MSW under jsdom). If a future PR adopts loaders/actions, the test harness must revisit this.
- **The question picker fetches an unfiltered library page (`size: 200`) to resolve already-selected questions' summaries** for the composition list, alongside the filtered page the picker itself shows — fine at authoring scale (mirrors the backend's own "fine at authoring scale" call on unfiltered load-then-filter, RFC-003), revisit only if an author's library genuinely grows past a few hundred questions.

---

# Open Questions

- **Guest identity on the client** — *resolved* (Phase 2 PR #4): `playerSessionStore` (Zustand, persisted, keyed by session PIN) holds the guest's `participantId` + `guestParticipantToken`; every mount with a stored record calls `reconnect` before rendering live content, and a `session.participant.not-found` response clears the stale record rather than retrying forever.
- **Realtime auth** — the `/ws/**` handshake is public today; when per-message authorization lands (RFC-005), `RealtimeClient.connect` likely gains connect headers carrying the JWT.
- **i18n** — content is multilingual server-side (RFC-003); UI-string localization is unaddressed and will need a decision before non-English congregations onboard.

---

# Acceptance Criteria

- [x] Application boots: real routing (7 routes + guard), providers, layouts, placeholder pages; production build passes.
- [x] Authentication: login/logout, protected routes, session-expiry handling (401 → clear → login with notice), persisted token; no refresh invented.
- [x] API layer: one axios instance (base URL, JWT, timeout, error mapping); api modules mirror the 25 real endpoints; types generated from OpenAPI; retries owned solely by TanStack Query.
- [x] Realtime: `RealtimeClient` with explicit connect, auto-reconnect + resubscription, heartbeats, RFC-005 parsing + version check, topic helpers; no gameplay subscriptions.
- [x] State ownership implemented as specified (token/connection/UI in Zustand; server state in Query; no duplication).
- [x] Design tokens with dark mode; shared generic components; zod + react-hook-form with shared schemas.
- [x] 95 tests green across providers, routing, auth, API client, realtime, error boundary, and the full quiz authoring, session hosting, gameplay, and results workflows; ESLint zero warnings; `tsc` clean.
- [x] Quiz authoring workflow (Phase 2 PR #2): list, create, edit metadata, search/attach/detach/reorder questions, review, publish, archive — end to end against the real backend, no mocks standing in for missing endpoints, no hand-written DTOs, no remaining TODOs for authoring capabilities.
- [x] `features/` module convention established and documented; optimistic updates scoped to composition only, never lifecycle transitions.
- [x] Session hosting workflow (Phase 2 PR #3): create a session from a published quiz, review details, open the lobby, watch presence arrive over STOMP, start server-confirmed — realtime-driven lobby with reconnect handling, no polling, no optimistic lifecycle transitions, no invented API contracts, no gameplay UI.
- [x] Live gameplay experience (Phase 2 PR #4): one FSM (`useGameplayState`) driving both gameplay screens; host monitors and progresses (`startQuestion`/`revealAnswer`/`showLeaderboard`/`advanceQuestion` sequenced by `useGameHost`, never optimistic); a participant joins by PIN, answers exactly once per question, is disabled on timeout and after submitting, and recovers a refresh or reconnect purely from the server's replay contract; no leaderboard, results, or final rankings render; the one new backend endpoint this PR required (`GET .../questions/current`) is public, phase-gated, and documented in RFC-004.
- [x] Results, leaderboards & session completion (Phase 2 PR #5): the FSM extended (never redesigned) with `ANSWER_REVEALED`/`LEADERBOARD`/`FINISHED`; the reveal renders server correctness, the viewer's own submission, their verdict, and the author's explanation; every leaderboard and the winner render the server's rankings verbatim through the new results read; both roles' completion screens (podium, final standings, summary, what-next actions) recover from a fresh mount with no event history; **Phase 2 is feature-complete** — the entire author → publish → host → play → results lifecycle works end to end.
- [x] Identity & host onboarding (Phase 3 PR #1): registration page; role-adaptive dashboard and navigation; profile and host-access routes; one shared `currentUser` query behind every role projection, invalidated by the one onboarding mutation; frontend authorization cosmetic throughout, 403s handled as real outcomes — the full register → become-a-host → author → host journey works with no development-only shortcuts.

---

# Future Work

Phase 2 is feature-complete; what follows is **Phase 3: Product Hardening** — production readiness, not user-facing capability. The frontend items already on this RFC's books that fold into it:

- **Backend session-list (and roster) endpoints** — retire `hostedSessionsStore` and the lobby's "joined before this view opened" row; both exist only because the contracts don't yet. (The *leaderboard* shows real display names since PR #5 — the results read carries them; the lobby roster is the remaining id-only surface.)
- **Session lifecycle timestamps** (started/finished) on the summary — the session-summary screen would then show duration and completion time instead of omitting them.
- **A per-answer verdict on the wire** — retires `verdictFor` (see Risks).
- **Question authoring UI** (create/edit) — the picker only searches and composes; the API surface exists, the UI doesn't yet.
- **Route-level code splitting** — was "before feature pages fatten the bundle"; they have, this is now due.
- **OpenAPI drift check in CI** — regenerate `api.gen.ts` and fail on diff.
- **Connect-time JWT on the realtime channel** when RFC-005 per-message authorization lands.
- **UI-string i18n**, PWA/offline, and accessibility audit — before public launch (PRD non-functionals).
