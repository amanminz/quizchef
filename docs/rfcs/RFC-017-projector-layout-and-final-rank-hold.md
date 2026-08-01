# RFC-017 Compact Projector Layout and the Final-Rank Hold

Status

Implemented

<!-- Draft | Proposed | Accepted | Implemented | Superseded by RFC-XXX
     Implemented — the whole scope shipped in one PR, the same precedent as
     RFC-010/011/013/014/015/016. See README.md for the lifecycle. -->

Authors

Aman Minz

Created

2026-08-01

Updated

2026-08-01

---

# Summary

Two production issues surfaced after the first live events on the Question Preview milestone (RFC-016): Host Presentation Mode needed scrolling on common projector resolutions because the answer timer alone claimed most of the screen, and participants could see their finishing rank before the host's podium ceremony started. This RFC fixes both. The layout fix replaces the full-size countdown with a compact, equal-weight `Answered`/`Time left` status row and trims spacing/typography so an active question fits one 1366×768 or 1920×1080 screen without scrolling. The privacy fix closes a gap in the "final results are held until the host releases them" rule (RFC-015): the hold only checked whether the *session* had reached `FINISHED`, not whether the *question in play* was the quiz's last one — so a participant's real rank was readable for the entire window between the last question's leaderboard appearing and the host clicking "Finish Quiz." A second, unrelated and more severe leak was found and closed in the same pass: the participant reconnect snapshot carried the *entire* named leaderboard, unconditionally, on every reconnect, at any point in a game — dead code on the frontend (nothing ever rendered it) but live on the wire.

---

# Motivation

A host running Presentation Mode from a laptop plugged into a classroom or church projector needs the whole question — prompt, both languages, options, and the essential controls — visible without touching a trackpad to scroll, and a live-event feedback session on RFC-016's rollout reported exactly that gap: the old full-size `ProjectorCountdown` (`clamp(4rem, 18vw, 13rem)` digits) pushed the option list off the bottom of a 768px-tall screen. Separately, the same event surfaced participants seeing their real finishing rank on their phones while the host was still mid-ceremony on the projector — undermining the whole point of PR #49's held-suspense podium reveal.

---

# Goals

- An active question (preview, open, or revealed) fits one projector viewport at 1366×768 and 1920×1080 with no page scrolling, without shrinking text below a safe minimum or truncating content.
- `Answered` and `Time left` render as one compact, equally-weighted row instead of a screen-dominating timer.
- No participant device can read its own final rank, or infer it from cached data, from the moment the quiz's last question's standings appear through to the host's explicit release — regardless of whether the session has technically reached `FINISHED` yet.
- Audit every participant-facing read for other undocumented leaks of ranking data, not just the one initially reported.

# Non-Goals

- Any change to scoring, ranking, podium order, or the ceremony's reveal pacing (PR #49/RFC-015 stays exactly as designed).
- Any change to `QUESTION_PREVIEW` timing or semantics (RFC-016 stays exactly as designed).
- A host "compact mode" toggle — the compact layout is simply what Presentation Mode now *is*; there is no separate legacy mode to preserve.
- True container-query-based responsive layout — approximated with ordinary viewport-width breakpoints (see Alternatives).
- Question Versioning (PR #47B) — still deferred.

---

# Proposed Design

## Layout: one shared metric primitive, not two bespoke ones

`PresentationMetric` (new) is a small labeled box — a label, an icon slot, and a big tabular-num value — with a fixed minimum width and a `tone` (`default`/`warning`/`critical`/`success`). Both of the compact row's metrics are literally this same component: `ProjectorCountdown` gained a `compact` prop that, when set, renders through `PresentationMetric` instead of its own giant markup (same urgency thresholds, same `role="timer"`/aria-label, just a different shell), and the host's `Answered` count is a second `PresentationMetric` instance. Building both from one primitive is what makes "Answered and Time left use the same visual size and hierarchy" true *by construction* rather than by two independently-tuned class lists drifting apart later. `ProjectorCountdown`'s original giant markup is untouched — `compact` defaults to `false`, so every existing caller and all seven of its prior tests are unaffected.

`QuestionHeader` composes the row: in Presentation Mode, `Answered` (when data exists) and the compact `Time left` render side by side where the old centered giant countdown block used to be, and the progress bar is dropped entirely — it isn't on the spec's essential-content list, and removing non-essential content is the cheapest, lowest-risk lever before touching typography. Outside Presentation Mode, `QuestionHeader` is byte-for-byte what it was.

**The reading-period placeholder.** During `QUESTION_PREVIEW` the backend genuinely has no answer-progress data yet (`AnswerProgressQueryService` already refuses that phase — RFC-016), so `answerProgress` is `undefined`. Rendering nothing there would mean the row's width changes the instant `QUESTION_OPEN` begins — exactly what the spec calls out as a layout jump to avoid. `QuestionHeader` takes a `previewing` flag and renders the `Answered` box with a literal `"–"` placeholder value whenever `previewing || answerProgress`, so the box is present with stable dimensions across the transition; only its *value* changes once real counts arrive.

## Viewport containment: a real height budget, not just smaller fonts

`SessionLivePage`'s Presentation Mode branch wraps its content in `h-dvh flex flex-col overflow-hidden` (the exact CSS the spec asked for) instead of only widening the container. `overflow-hidden` here is a safety net, never the fitting mechanism — the spec is explicit that hiding overflow instead of doing the responsive work is not acceptable, so the header, the question card, and the options list all cooperate to actually fit: the header and connection banner are `shrink-0`, the live-question area is `min-h-0 flex-1` (the standard flexbox trick that lets a flex child actually shrink inside a fixed-height parent instead of forcing the parent to grow), and `HostBilingualQuestion`'s own `Card`/`CardContent` repeat the same `flex h-full min-h-0 flex-col` pattern down to the options `<ol>`.

`tailwind-merge` (already the project's `cn()` implementation) resolves the conflicting utility classes correctly — a caller-supplied `py-3` genuinely overrides a component's base `py-8` because `cn` merges by Tailwind utility group, not string concatenation order — which is what makes composing "the normal padding" and "the compact padding" through the same `className` prop safe.

## Responsive text: clamp() where the spec asked for it, Tailwind steps everywhere else

The prompt, secondary-language prompt, option text, and reveal explanations get `clamp()`-based inline font sizes in Presentation Mode specifically (e.g. the primary prompt: `clamp(1.1rem, 2.6vw, 2.1rem)`) — smaller ceilings than the normal-layout `sm:`/`lg:` steps, and viewport-aware rather than fixed. Everything else (padding, gaps, badge sizes) uses ordinary smaller Tailwind steps swapped in behind a `presentationActive` conditional, which is the established idiom throughout this codebase and needs no new tooling. Reveal-time per-option counts already rendered in the spec's own example format (`7 · 70%`) before this change; they only needed the same padding/typography treatment as everything else, not a new format.

**Bilingual side-by-side layout** uses a plain `lg:flex-row` on the prompt block in Presentation Mode — an approximation of "side-by-side when there's sufficient *width*," not a true "sufficient width *for the current height*" container query (Tailwind 3.4, the project's installed version, has no first-class container-query support without an additional plugin). This is a deliberate, documented simplification (see Alternatives): Presentation Mode is host-only and always used on a laptop feeding a landscape display, so a width breakpoint is a reasonable proxy for "there's room," without adding new build tooling for a hotfix.

## Privacy: two separate leaks, two separate closes

**1. `SessionResultsQueryService.personalResultReadable`** checked only `SessionState`/`SessionPhase` — `IN_PROGRESS` + (`ANSWER_REVEALED` or `LEADERBOARD`) was always readable, with no notion of *which* question was in play. `ParticipantRankContextQueryService` (RFC-015) already had the correct, narrower rule for ranking neighbours — "never for the quiz's last question, full stop" — computed via the same `QuestionProgression.nextAfter(quiz, currentQuestionId).isEmpty()` the gameplay engine already uses to decide when to finish a session. `personalResultReadable` now applies the identical check while still `IN_PROGRESS`: on the last question, `ANSWER_REVEALED`/`LEADERBOARD` are held exactly like `FINISHED`-but-not-released is. The two holds are now the same rule, checked at two moments the state machine happens to pass through.

The frontend mirrors this: `useParticipantResult` gained an `isLastQuestion` parameter (identical shape to `useRankContext`'s existing one), and `PlaySessionPage`'s `LEADERBOARD` case checks `isLastQuestion(question)` before ever touching `personalResult`/`rankContext` — routing straight to the same `FinalResultsPendingScreen` the post-`FINISHED` pending state already used. Because the query is `enabled: false` in this window (not merely un-rendered), the network call itself never fires — verified directly in the new tests, not inferred from the UI.

**2. `SessionSnapshotAssembler`** (the `/reconnect` response every participant device calls after a join, a refresh, or a dropped connection) computed the *entire* ranked roster — every participant's name, score, and rank — via `leaderboardService.rank(...)` and returned it unconditionally, regardless of session state, phase, or the release gate. This predates both this RFC and RFC-016; it was not the reported bug, but the same "audit every participant-facing response" instruction that scoped this RFC's privacy work turned it up. The frontend never read the field (confirmed by search — no component destructures `.leaderboard` from the reconnect snapshot), so nothing downstream needed it; it was pure unnecessary exposure on the wire, at every point in every game, to every participant. `SessionSnapshotAssembler` now always returns `List.of()` for `leaderboard` — the same "notification, not a data source" shape `LeaderboardUpdatedEvent`'s broadcast payload already uses — and no longer depends on `ParticipantRepository`/`LeaderboardService` at all, removing the unnecessary roster fetch along with the leak. The field itself stays in the API shape (avoiding a breaking response-schema change for a hotfix); only its population changed.

---

# Alternatives Considered

**A host "skip reading time" or "compact mode" toggle for the projector layout** — rejected: there's no legacy layout worth preserving behind a flag; the old layout was simply a bug (didn't fit the screen), not a design some hosts might prefer.

**True container queries for the bilingual side-by-side layout** — rejected for this hotfix: correct in principle (it reasons about the actual available box, not just viewport width), but requires adding `@tailwindcss/container-queries` (not currently a dependency) purely to solve one layout decision in a host-only, always-landscape context where a width breakpoint already behaves correctly in practice.

**Removing the `leaderboard` field from `SessionSnapshotResponse` entirely** — rejected: a confirmed-dead field is still a breaking response-shape change, out of proportion for a hotfix whose other three call sites are additive. Emptying it (this RFC's approach) closes the actual leak with zero API-shape risk; removing it outright is reasonable future cleanup once nothing could plausibly depend on its presence.

**Holding `personalResult` for the last question's `ANSWER_REVEALED` only, not `LEADERBOARD`** — considered and rejected: the reported defect was specifically visible at `LEADERBOARD`, but `ANSWER_REVEALED` is equally IN_PROGRESS and equally "not yet the ceremony," and `ParticipantRankContextQueryService` already treats both phases identically for the same question. A split rule between two structurally identical read services would be a foot-gun for the next contributor; one rule, checked the same way in both places, is simpler to reason about and to keep correct.

---

# Risks

- The `clamp()` minimums were chosen by reasoning about the content (prompt/option lengths seen in existing fixtures), not by measuring real rendering at 1366×768/1920×1080 — jsdom has no layout engine, so automated tests can only assert DOM structure and class/style presence, never actual pixel fit. Manual verification (the spec's own checklist) is the real acceptance test for this half of the RFC.
- `usePresentationStore` is a module-level Zustand singleton with no automatic per-test reset; a test that enters Presentation Mode and doesn't reset the store leaks `active: true` into whatever test runs next in the same file. Not a new risk introduced here — an existing pattern (`SessionLobbyPage.test.tsx` already resets it) — but easy to trip over, and this PR's own new tests hit it once during development. Documented here so the next contributor recognizes the symptom immediately (a "such-and-such button not found" failure right after a Presentation Mode test) instead of re-diagnosing it.
- The two privacy fixes narrow when data is *readable*; they do not retroactively invalidate anything a participant's device already cached client-side before this fix. Not a practical concern (this ships as a full deploy, not a partial rollout), but worth naming.

---

# Migration

None. No database schema changed — the fix to `SessionSnapshotAssembler` and `SessionResultsQueryService` is pure application-layer logic, and every frontend change is a new prop, a new hook parameter, or a new conditional render path, all backward compatible with existing callers.

---

# Acceptance Criteria

- [x] `PresentationMetric` renders `Answered` and `Time left` at identical numeral font sizes (asserted directly by comparing computed inline styles in tests) and a shared fixed minimum width.
- [x] `ProjectorCountdown`'s existing seven tests pass unchanged; new `compact` tests cover the same urgency/no-color-alone guarantees through the compact shell.
- [x] The progress bar is present in the normal layout and absent in Presentation Mode.
- [x] The `Answered` box shows a stable placeholder through the `QUESTION_PREVIEW` → `QUESTION_OPEN` transition, never disappearing and reappearing.
- [x] Reveal-phase per-option counts still render inline in Presentation Mode.
- [x] `personalResultReadable` refuses on the last question's `ANSWER_REVEALED`/`LEADERBOARD` while still `IN_PROGRESS`, proven at both the unit level (`SessionResultsQueryServiceTest`) and the full HTTP integration level with a real quiz and real question ordering (`GameplayIntegrationTest`), plus confirmation that a *non*-final question's interim standings read is completely unaffected.
- [x] `useParticipantResult`/`PlaySessionPage` never call `personalResult` or `rankContext` while the last question's standings are held — verified by network-call assertions, not just DOM absence — and a refresh/reconnect during that exact window still shows no rank.
- [x] `SessionSnapshotAssembler`'s reconnect snapshot always returns an empty `leaderboard`, verified in `ReconnectParticipantApplicationServiceTest`.
- [x] PR #49/RFC-015's ceremony, ranking, and podium-order tests all pass unmodified — nothing about the ceremony itself changed.

---

# Future Work

- Remove the now-always-empty `leaderboard` field from `SessionSnapshotResponse` outright, once a deliberate API-versioning pass makes a breaking response-shape change acceptable.
- Real container-query-based responsive layout, if a future host-facing layout decision needs to reason about available height, not just width.
- Question Versioning (PR #47B) — begins only after this PR is reviewed and the fixes are confirmed stable in a live event.
