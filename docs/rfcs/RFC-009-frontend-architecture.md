# RFC-009 Frontend Architecture

Status

Implemented

<!-- Draft | Proposed | Accepted | Implemented | Superseded by RFC-XXX
     Implemented — the React platform foundation (Phase 2 PR #1) ships:
     routing, providers, auth, API layer, realtime client, state ownership,
     design tokens, shared components, and the test stack. Feature UIs
     (authoring, lobby, gameplay) are later PRs building ON this — they do
     not hold this RFC back. See README.md for the lifecycle.
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

Phase 2 PR #1 implements all of it as **infrastructure only**: no quiz authoring UI, no lobby, no gameplay. Pages exist as placeholders behind real routing, real auth, and a real design system.

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

- Feature UIs — quiz authoring (PR #2), lobby, gameplay, leaderboard, results are later Phase 2 PRs.
- Token refresh and server-side logout — the backend has neither (RFC-002 Future Work); see Authentication for what happens instead.
- Accessibility polish, animations, media, church branding (deferred by the phase spec; the primitives are accessible-by-default where cheap: labeled fields, roles, focus rings, native `<dialog>`).
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
  api/        axios instance, apiError mapping, identityApi / quizApi / sessionApi
  auth/       AuthContext, AuthProvider, useAuth, useCurrentUser, RequireAuth, authStore
  realtime/   RealtimeClient, RealtimeContext, RealtimeProvider, useRealtime, SessionSubscriptions, connectionStore
  hooks/      cross-cutting hooks (useOnlineStatus)
  layouts/    PublicLayout, DashboardLayout
  pages/      one component per route (placeholders this PR)
  components/ common/ forms/ feedback/ navigation/ — generic only, nothing quiz-specific
  theme/      tokens.css, uiPreferencesStore, useApplyTheme
  types/      api.gen.ts (generated), api.ts (aliases), protocol.ts (RFC-005, hand-written by design)
  utils/      cn, env, validation
  test/       setup, MSW server + handlers, testUtils
```

## Routing

React Router v7, data router in the app (`createBrowserRouter`), one exported route table:

```text
/            home            public
/login       sign in         public
/play        join a game     public (guests are first-class, ADR-003)
/dashboard   host home       RequireAuth
/quizzes     authoring       RequireAuth
/sessions    hosting         RequireAuth
/not-found + catch-all       public
```

`RequireAuth` is a layout route: unauthenticated visitors are redirected to `/login` carrying the path they wanted, and login returns them there. The route table is exported so tests mount the identical tree in a memory router.

## State ownership

The load-bearing table. Each kind of state has exactly one owner, and the same datum never lives in two stores:

| State | Owner |
| --- | --- |
| Authentication (the JWT, session-expired flag) | Zustand (`authStore`, persisted) |
| Current route | React Router |
| Backend resources (current user, quizzes, sessions…) | TanStack Query |
| Realtime connection status | Zustand (`connectionStore`) |
| Live session projections (future gameplay) | TanStack Query, updated from realtime events |
| UI preferences (theme) | Zustand (`uiPreferencesStore`, persisted) |
| Component-local UI (open modal, form input) | React state |

The proof of the rule: `useCurrentUser` is a TanStack Query over `/users/me`; the auth store holds **only the token**. Logging in stores a token and invalidates queries — it never copies the user into Zustand. When gameplay arrives, realtime events will **invalidate or write into the query cache**, so server state keeps exactly one home even when it is pushed rather than fetched.

## Authentication

- **Login** stores the session-bound JWT (RFC-002) in the persisted auth store; a refresh keeps the login.
- **Expiry**: any non-auth 401 means the server-side identity session is gone (revoked or expired). The axios interceptor clears local auth and sets `sessionExpired`; `RequireAuth` routes to `/login`, which explains why. There is **no token refresh** because the backend issues no refresh tokens — recorded here so nobody "adds" one client-side.
- **Logout** is client-side token discard: the backend has no logout endpoint yet. Tokens are session-bound server-side, so revoking the identity session remains the server's kill switch. When RFC-002's logout lands, `AuthProvider.logout` gains one API call and nothing else changes.
- **Storage**: localStorage, versioned key. The XSS trade-off is accepted at this scale and mitigated by React's escaping and no third-party scripts; the session-bound design caps the blast radius of a stolen token.

## API layer

One axios instance (`api/axios.ts`) centralizes the base URL (same-origin by default; the dev server proxies `/api` and `/ws` to the backend), JWT injection from the auth store, a 10s timeout, and **error mapping**: every failure becomes an `ApiClientError` carrying the backend's stable `code`, the HTTP status, and field errors — callers switch on codes, never on transport artifacts. **Retries belong to TanStack Query alone** (Providers: 4xx never retried, network/5xx twice), so no request is ever retried at two layers.

**Generated types.** `OpenApiSpecExportTest` (backend) exports the live OpenAPI document to `backend/app/build/openapi.json`; `npm run generate:api` turns it into `types/api.gen.ts` (committed, so the frontend builds standalone). The api modules (`identityApi`, `quizApi`, `sessionApi`) mirror the 25 real endpoints exactly — no invented list endpoints, no hand-written DTOs. When the backend API changes, the pipeline makes the frontend fail to compile instead of silently drifting.

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

Generic only: `Button` (cva variants, loading state), `Card` family, `Modal` (native `<dialog>` — platform focus trap/ESC/top-layer), `ConfirmDialog`, `Spinner`, `LoadingOverlay`, `PageContainer`, `SectionHeader`, `EmptyState`, `ErrorPanel`, `FormField` (react-hook-form-ready), `ErrorBoundary`, `OfflineIndicator`, `AppNav`. Nothing quiz-specific exists in `components/` — feature components will live with their features.

## Forms & validation

react-hook-form + zod via a `zodForm(schema)` helper; shared field schemas (`emailSchema`, `passwordSchema` 8–128, `sessionPinSchema` 6 digits, `displayNameSchema`) mirror the backend's rules for faster feedback — **the server remains the authority** (ADR-006 spirit: client validation is UX, not truth).

## Error handling

Four layers, each with one job: the **axios interceptor** normalizes every API failure to `ApiClientError`; **TanStack Query** surfaces `isError`/`error` to pages, which render `ErrorPanel` with retry; the global **ErrorBoundary** catches render-time throws; **OfflineIndicator** shows loss of connectivity. Unauthorized (401) is handled once, in the interceptor, as session expiry.

## Testing strategy

Vitest + Testing Library + MSW (network mocked at the boundary — the app's real axios stack runs). `renderApp()` mounts the **real route table inside the real provider stack** in a memory router, so tests exercise routing, auth, queries, and interceptors together; MSW's `onUnhandledRequest: "error"` fails any test that talks to an unmocked endpoint. `RealtimeClient` is tested against a scriptable fake STOMP client injected through its factory seam (connect/reconnect/resubscribe/dispatch/unsubscribe). 23 tests cover routing, the full login/logout/expiry flows, API error mapping, the realtime lifecycle, and the error boundary. One deliberate wrinkle: tests mount the route tree through the declarative `MemoryRouter` because the data router's Request machinery clashes with MSW under jsdom — same tree, same guards, no lost coverage.

---

# Alternatives Considered

**Hand-written DTO interfaces** — rejected: they drift silently. Generated types turn API drift into compile errors (Aman's recommendation #1).

**Components using STOMP directly / a subscription hook per feature** — rejected: transport details would spread through feature code and every feature would re-solve reconnection. One `RealtimeClient` mirrors the backend's ADR-004 seam (Aman's recommendation #2).

**Everything in Zustand / everything in Query / Redux** — rejected: server state in a client store must be manually invalidated (and won't be); client state in Query gains nothing. The ownership table (Aman's recommendation #3) gives each kind of state the tool built for it. Redux adds ceremony without adding an owner.

**A component library (MUI, Mantine, full shadcn/ui)** — rejected for now: the phase spec asks for a lightweight system; tokens + Tailwind + a dozen primitives cover the platform. The shadcn-compatible conventions keep that door open.

**Token in memory only (no persistence)** — rejected: every refresh would log the host out mid-session prep. localStorage with a versioned key is the pragmatic church-scale choice; the trade-off is documented under Authentication.

**SockJS client with fallback transports** — not needed: modern browsers all speak WebSocket; the raw-WebSocket transport of the existing SockJS endpoint works today, and a fallback would be an implementation detail of `RealtimeClient` if ever required.

**Auto-connecting realtime at app boot** — rejected: a visitor reading the home page holds no session; connections are opened by the features that need them.

---

# Risks

- **The generated-types pipeline needs the backend build** (`OpenApiSpecExportTest` → `generate:api`). Mitigated: `api.gen.ts` is committed, so the frontend builds without the backend; regeneration is a documented two-command step. CI wiring for drift detection is future work.
- **localStorage JWT** is readable by successful XSS. Accepted and documented; session-bound tokens cap the damage, and no third-party scripts run.
- **Single bundle (~750 kB min)** — fine for the platform shell; route-level code splitting (`React.lazy`) is listed as future work before feature pages fatten it.
- **jsdom test wrinkle**: tests use the declarative router (data-router Requests clash with MSW under jsdom). If a future PR adopts loaders/actions, the test harness must revisit this.

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
- [x] 23 tests green across providers, routing, auth, API client, realtime, error boundary; ESLint zero warnings; `tsc` clean.
- [x] No feature UI (quiz/lobby/gameplay pages are placeholders).

---

# Future Work

- **Quiz authoring UI (Phase 2 PR #2)** — the first feature consumer of this platform.
- **Route-level code splitting** once feature pages land.
- **OpenAPI drift check in CI** — regenerate `api.gen.ts` and fail on diff.
- **Registration page** — the endpoint and validation schemas exist; the page arrives with the first onboarding-focused PR.
- **Connect-time JWT on the realtime channel** when RFC-005 per-message authorization lands.
- **UI-string i18n**, PWA/offline, and accessibility audit — before public launch (PRD non-functionals).
