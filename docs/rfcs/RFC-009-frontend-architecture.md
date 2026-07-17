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
     first realtime consumer; it too changed no PR #1 contract. Gameplay UI
     is a later PR; it does not hold this RFC back. See README.md for the
     lifecycle.
     Note: the Phase 2 spec named this "RFC-007", but RFC-007 is Media
     Storage and RFC-008 is Deployment; the next free number is 009. -->

Authors

Aman Minz

Created

2026-07-17

Updated

2026-07-17

---

# Summary

Defines the architecture of the QuizChef web frontend: a React 19 + TypeScript + Vite application in `frontend/` that is a **client of the platform, never part of it**. The backend is server-authoritative for everything that matters (ADR-006); the frontend renders state, submits commands, and receives projections. This RFC fixes the five contracts every future frontend PR builds on — the layering, the route table, **state ownership**, the API layer (with OpenAPI-generated types), and the realtime layer (one `RealtimeClient` over RFC-005) — plus the testing strategy that keeps them honest.

Phase 2 PR #1 implemented all of it as **infrastructure only**: no quiz authoring UI, no lobby, no gameplay. Phase 2 PR #2 (Quiz Authoring) is the first feature built on it, and in doing so establishes the **feature module** convention (`features/<name>/`) this RFC now documents alongside the original five contracts. Phase 2 PR #3 (Host Dashboard & Session Creation) is the second feature module and the first consumer of the realtime layer — its lobby realized the "live session projections" row of the state-ownership table; gameplay follows the same shape.

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

- Gameplay, leaderboard, results UI — Phase 2 PR #4, building on the feature-module convention PR #2 established and the realtime patterns PR #3's lobby established. (The lobby itself shipped in PR #3.)
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
  features/   <name>/ — hooks/, components/, queryKeys.ts (PR #2 established this; quizzes/, sessions/)
  theme/      tokens.css, uiPreferencesStore, useApplyTheme
  types/      api.gen.ts (generated), api.ts (aliases), protocol.ts (RFC-005, hand-written by design)
  utils/      cn, env, validation
  test/       setup, MSW server + handlers, testUtils, quizFixtures, sessionFixtures, fakeStomp
```

**The `features/` convention (added Phase 2 PR #2).** `components/` stays generic by rule (RFC-009 already said "nothing quiz-specific"); a real feature needs its own hooks, presentational components, and query-key registry, and none of those belong in the cross-cutting `hooks/`/`components/` folders either. `features/<name>/` is where they live: `features/quizzes/hooks/` (query hooks + orchestration hooks), `features/quizzes/components/` (feature-specific presentational components), `features/quizzes/queryKeys.ts` (the one place a query's key and a mutation's invalidation target are defined, so they can't drift from each other by a typo). Lobby and gameplay will each get their own `features/<name>/` the same way.

## Routing

React Router v7, data router in the app (`createBrowserRouter`), one exported route table:

```text
/                          home                       public
/login                     sign in                    public
/play                      join a game                public (guests are first-class, ADR-003)
/dashboard                 host home                  RequireAuth
/quizzes                   "My Quizzes"                RequireAuth
/quizzes/new               create a draft             RequireAuth
/quizzes/:quizId           edit metadata               RequireAuth
/quizzes/:quizId/questions compose (search + reorder)  RequireAuth
/quizzes/:quizId/review    read-only summary + publish RequireAuth
/sessions                  hosting dashboard           RequireAuth
/sessions/new              create a session            RequireAuth
/sessions/:sessionId       session details             RequireAuth
/sessions/:sessionId/lobby the host's lobby            RequireAuth
/sessions/:sessionId/play  gameplay (placeholder)      RequireAuth
/not-found + catch-all                                public
```

`RequireAuth` is a layout route: unauthenticated visitors are redirected to `/login` carrying the path they wanted, and login returns them there. The route table is exported so tests mount the identical tree in a memory router. `/quizzes/new` is registered before `/quizzes/:quizId`, but the order is cosmetic — React Router (like the backend's own routing) resolves a literal segment over a variable one at the same level regardless of declaration order.

## State ownership

The load-bearing table. Each kind of state has exactly one owner, and the same datum never lives in two stores:

| State | Owner |
| --- | --- |
| Authentication (the JWT, session-expired flag) | Zustand (`authStore`, persisted) |
| Current route | React Router |
| Backend resources (current user, quizzes, sessions…) | TanStack Query |
| Realtime connection status | Zustand (`connectionStore`) |
| Live session projections (lobby today, gameplay next) | TanStack Query, updated from realtime events |
| Transient lobby presence (the event-built roster) | Realtime only (`useParticipants` component state) |
| Hosted-session ids (which sessions this browser created) | Zustand (`hostedSessionsStore`, persisted) |
| UI preferences (theme) | Zustand (`uiPreferencesStore`, persisted) |
| Component-local UI (open modal, form input) | React state |

The proof of the rule: `useCurrentUser` is a TanStack Query over `/users/me`; the auth store holds **only the token**. Logging in stores a token and invalidates queries — it never copies the user into Zustand. The lobby (Phase 2 PR #3) realized the "projections" row: realtime events **invalidate or write into the query cache**, so server state keeps exactly one home even when it is pushed rather than fetched. The two PR #3 additions honor the same exclusivity: `hostedSessionsStore` holds only ids (a client-side fact — session data stays in Query), and the event-built roster is deliberately not server state at all (see Session hosting below).

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

- **connect / disconnect** are explicit. Nothing connects at app boot — realtime is only needed while a session is live, and the gameplay PRs own that lifecycle.
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

## Forms & validation

react-hook-form + zod via a `zodForm(schema)` helper; shared field schemas (`emailSchema`, `passwordSchema` 8–128, `sessionPinSchema` 6 digits, `displayNameSchema`) mirror the backend's rules for faster feedback — **the server remains the authority** (ADR-006 spirit: client validation is UX, not truth).

## Error handling

Four layers, each with one job: the **axios interceptor** normalizes every API failure to `ApiClientError`; **TanStack Query** surfaces `isError`/`error` to pages, which render `ErrorPanel` with retry; the global **ErrorBoundary** catches render-time throws; **OfflineIndicator** shows loss of connectivity. Unauthorized (401) is handled once, in the interceptor, as session expiry.

## Testing strategy

Vitest + Testing Library + MSW (network mocked at the boundary — the app's real axios stack runs). `renderApp()` mounts the **real route table inside the real provider stack** in a memory router, so tests exercise routing, auth, queries, and interceptors together; MSW's `onUnhandledRequest: "error"` fails any test that talks to an unmocked endpoint. `RealtimeClient` is tested against a scriptable fake STOMP client injected through its factory seam (connect/reconnect/resubscribe/dispatch/unsubscribe). One deliberate wrinkle: tests mount the route tree through the declarative `MemoryRouter` because the data router's Request machinery clashes with MSW under jsdom — same tree, same guards, no lost coverage.

**75 tests** (23 from PR #1 + 28 from PR #2's authoring workflow + 24 from PR #3's hosting workflow): routing/auth/API-client/realtime/error-boundary from PR #1, plus every authoring page (list lifecycle-sectioning and its own empty/error states, create validation and error surfacing, metadata load/save/version-conflict, question search/attach/detach, publish/archive behind confirmation with its precondition warning) and every new route's auth redirect. PR #3 covers the hosting workflow end to end: the dashboard's lifecycle sectioning, empty state, and 404 pruning; create-session (published-only listing, metadata panel, the server-confirmed create, the 409 surface); session details with open-lobby and its authorization failure; and the lobby against the fake STOMP transport — roster join/disconnect events with the event-then-reconcile refetch, the pre-subscription count row, the reconnect banner, the start flow's server confirmation, its 403 rejection, and the remote `session.started` navigation. The fake transport moved from `RealtimeClient.test.ts` into `test/fakeStomp.ts` once the lobby tests needed it, and `renderApp` gained an injection seam for a fake-backed `RealtimeClient`. **`useQuestionSelection`'s reorder is tested at the hook level** (`renderHook`, a scriptable MSW handler with an artificial delay to make the optimistic window observable) rather than by simulating a drag gesture in jsdom — dnd-kit's pointer events don't translate meaningfully to jsdom's synthetic event system, and the actual logic under test (optimistic update → rollback on failure) lives in the hook, not in dnd-kit's own gesture handling (which the library's own tests already cover).

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

---

# Risks

- **The generated-types pipeline needs the backend build** (`OpenApiSpecExportTest` → `generate:api`). Mitigated: `api.gen.ts` is committed, so the frontend builds without the backend; regeneration is a documented two-command step. CI wiring for drift detection is future work.
- **localStorage JWT** is readable by successful XSS. Accepted and documented; session-bound tokens cap the damage, and no third-party scripts run.
- **Single bundle (~918 kB min after PR #3, up from ~864 kB)** — still fine for church scale, but the code-splitting future work is now more urgent than "before feature pages fatten it" — they have.
- **The hosted-session registry is per-browser.** A host who switches devices (or clears storage) loses the dashboard's memory of their sessions — the sessions themselves live on, reachable by id/PIN. This is the honest cost of not inventing a list endpoint; it disappears the day the backend grows one.
- **The lobby roster shows ids, not names.** RFC-005's roster events deliberately carry only the participant id; until a roster read model or richer join event exists, the lobby renders id-derived avatars and the count row. Acceptable for the host's "how many are in?" question; revisit with the gameplay PR, which needs display names anyway (leaderboard).
- **jsdom test wrinkle**: tests use the declarative router (data-router Requests clash with MSW under jsdom). If a future PR adopts loaders/actions, the test harness must revisit this.
- **The question picker fetches an unfiltered library page (`size: 200`) to resolve already-selected questions' summaries** for the composition list, alongside the filtered page the picker itself shows — fine at authoring scale (mirrors the backend's own "fine at authoring scale" call on unfiltered load-then-filter, RFC-003), revisit only if an author's library genuinely grows past a few hundred questions.

---

# Open Questions

- **Guest identity on the client** — where the guest participant token from `join` is kept (memory vs storage) and how `/play` reconnects after a refresh: decided in the gameplay UI PR, on top of the auth-store pattern established here.
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
- [x] 75 tests green across providers, routing, auth, API client, realtime, error boundary, and the full quiz authoring and session hosting workflows; ESLint zero warnings; `tsc` clean.
- [x] Quiz authoring workflow (Phase 2 PR #2): list, create, edit metadata, search/attach/detach/reorder questions, review, publish, archive — end to end against the real backend, no mocks standing in for missing endpoints, no hand-written DTOs, no remaining TODOs for authoring capabilities.
- [x] `features/` module convention established and documented; optimistic updates scoped to composition only, never lifecycle transitions.
- [x] Session hosting workflow (Phase 2 PR #3): create a session from a published quiz, review details, open the lobby, watch presence arrive over STOMP, start server-confirmed — realtime-driven lobby with reconnect handling, no polling, no optimistic lifecycle transitions, no invented API contracts, no gameplay UI.

---

# Future Work

- **Live gameplay UI (Phase 2 PR #4)** — question presentation, countdowns against `endsAt`, reveal, leaderboard; takes over the realtime connection lifecycle from the lobby and gives the participant `/play` flow its own hooks.
- **Backend session-list (and roster) endpoints** — retire `hostedSessionsStore` and the lobby's "joined before this view opened" row; both exist only because the contracts don't yet.
- **Question authoring UI** (create/edit) — the picker only searches and composes; the API surface exists, the UI doesn't yet.
- **Route-level code splitting** — was "before feature pages fatten the bundle"; they have, this is now due.
- **OpenAPI drift check in CI** — regenerate `api.gen.ts` and fail on diff.
- **Registration page** — the endpoint and validation schemas exist; the page arrives with the first onboarding-focused PR.
- **Connect-time JWT on the realtime channel** when RFC-005 per-message authorization lands.
- **UI-string i18n**, PWA/offline, and accessibility audit — before public launch (PRD non-functionals).
