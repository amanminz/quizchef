# RFC-015 Live Results UX: Projector Timer, Answer Distribution, Ranking Neighbours, and Winner Ceremony

Status

Implemented

<!-- Draft | Proposed | Accepted | Implemented | Superseded by RFC-XXX
     Implemented — the whole scope shipped in one PR (#47A), the same
     precedent as RFC-010/011/013/014. See README.md for the lifecycle. -->

Authors

Aman Minz

Created

2026-07-31

Updated

2026-07-31

---

# Summary

The first production quiz event succeeded, and surfaced four gaps: the host's countdown is unreadable from the back of a room; participants have no idea how the group answered; participants either learn nothing about their standing mid-game or (worse) would see their exact final rank the instant the last question ends, spoiling the "winners announced" moment the host wants to run as a ceremony. This RFC closes all four, entirely as new backend-authoritative reads and one new host command layered onto the existing session/gameplay engine (RFC-004) and realtime protocol (RFC-005) — no change to scoring, tie-breaking, authentication, transport, or the localization model.

---

# Motivation

Every gap here is a live-event UX problem the church surfaced by actually running the product, not a hypothetical. A projector 30 feet from the last row needs numerals a phone screen never has to consider. A host running a live event wants to *narrate* what the room did ("look how many picked B!"), not just move to the next question. Participants investing attention in a multi-question quiz want *some* signal of how they're doing without the product handing them a full leaderboard (privacy) or the exact final placement before the host gets to announce it (product moment). All four are additive reads over data the engine already computes (accepted answers, cached scores) or a single new host action (release) — nothing here changes what an answer means or how a score is computed.

---

# Goals

- A countdown the back row can read, with escalating non-color urgency, without touching the compact in-flow timer non-presentation layouts already use.
- Authoritative per-option answer counts on the host's reveal screen, consistent across every authored language.
- A participant's own rank plus immediate neighbours after a non-final question — narrower than the pre-existing personal-result read, never the full leaderboard.
- A backend-enforced hold on final standings until the host explicitly releases them, with a five-place staged ceremony (5th → 1st) preceding release.

# Non-Goals

- Question Versioning (draft revisions, immutable published versions, quiz version pinning) — the next milestone (PR #47B), explicitly deferred.
- Any change to `ScoringService`, `ScoringPolicy`, or `LeaderboardService`'s ranking/tie-break algorithm — reused exactly as RFC-006 defined it.
- Any change to the gameplay phase machine (`QUESTION_OPEN → QUESTION_CLOSED → ANSWER_REVEALED → LEADERBOARD`) or the session lifecycle (`CREATED → LOBBY → IN_PROGRESS → FINISHED → ARCHIVED`) — both are extended with one boolean, never redesigned.
- Completed-session-history schema work — explicitly out of scope per the milestone guardrails; this RFC does not combine with it.

---

# Proposed Design

## Final-results hold: one boolean, not a new state

`Session` gains `finalResultsReleased: boolean`, default `false`, reset to `false` by `finish()`, flipped only by the new idempotent `Session.releaseFinalResults()` (a no-op once already `true`; otherwise requires `state == FINISHED`). A genuinely new `SessionState` value was rejected (see Alternatives) — the boolean is additive, needs no `state` CHECK-constraint migration, and every existing `FINISHED`/`ARCHIVED` comparison site stays correct as-is.

Crucially, **the host's full standings read is never gated by this flag** — `SessionResultsQueryService.results` (host, `/results`) keeps its existing `resultsReadable` gate (`FINISHED`/`ARCHIVED`, or `IN_PROGRESS` at `ANSWER_REVEALED`/`LEADERBOARD`), because the host needs the full standings the instant the session finishes to run the winner ceremony — *before* clicking release. Only `SessionResultsQueryService.personalResult` (participant, `/participants/{id}/result`) gets the new `personalResultReadable` check: at `FINISHED`/`ARCHIVED`, readable only if `finalResultsReleased`. This asymmetry is the one subtle piece of this RFC — get it backwards and either the ceremony can't run, or a participant's rank leaks early.

`POST /sessions/{id}/results/release` (`ReleaseFinalResultsApplicationService`, host-only — `QUIZ_HOST` + `SessionHostPolicy`, the same pattern every other host command follows) calls `releaseFinalResults()` and publishes the one genuinely new domain event, `FinalResultsReleasedEvent` → wire type `final.results.revealed`. Every other transition this RFC cares about already has an event that fires at the right moment (`SessionFinishedEvent` → `session.finished` for "session just ended"; `AnswerRevealedEvent` → `answer.revealed` for "distribution/rank-context just became readable") — adding new broadcasts for those would be redundant, so the frontend simply adds those keys to its existing invalidation sets.

`V10__final_results_hold.sql` backfills every session that finished **before** this feature existed as already-released (nobody is waiting on a ceremony for an event that already happened); every session finishing from now on starts pending.

## Answer distribution

`GET /sessions/{id}/answer-distribution` (host-only, `AnswerDistributionQueryService`) mirrors `AnswerProgressQueryService`'s shape (host authz, current-question lookup) but tallies `ParticipantAnswer.selectedOptionIds` per option, against the universe of option ids from `GameplayQuizQuery` (so an option nobody picked still reports `count: 0`, never a missing row). Gated to `ANSWER_REVEALED`/`LEADERBOARD` (`409 session.distribution.not-available` before) — the same reveal-time discipline as full results, since counts alongside `correctOptionIds` would leak who's right before the reveal broadcast does. Because an accepted answer exists exactly once per participant per question (`Participant.recordAnswer` rejects a second answer to the same question), duplicates and rejections structurally cannot inflate a count. For a multiple-answer question, `sum(options[].count)` may exceed `answeredCount` by design — one participant, several selections — documented on the view and the response schema.

## Ranking neighbours

`GET /sessions/{id}/participants/{participantId}/rank-context` (public, unguessable-id gated exactly like `personalResult` — `ParticipantRankContextQueryService`) reuses `LeaderboardService.rank(...)` unchanged: no new ranking math, just a narrower projection. Given the full ranked list, it locates the caller's entry and reports the adjacent list entries as `ahead`/`behind` (with the score gap), collapsing either one into `tiedWith` instead when the adjacent entry's score is *equal* — because `LeaderboardEntry.rank` is dense and fully tie-broken (submission time, then join order), so two entries never literally share a numeric rank even when their scores tie; "tied" in the product sense is a score comparison, not a rank comparison.

Gated on two independent conditions: `IN_PROGRESS` at `ANSWER_REVEALED`/`LEADERBOARD`, **and** the current question is not the quiz's last (checked via the existing `QuestionProgression.nextAfter(...).isEmpty()`) — the last-question check is unconditional, so neighbours are structurally unavailable on the final question regardless of phase or release state, satisfying "never after the final question" without depending on the release flag at all.

## Countdown timer

`ProjectorCountdown` (frontend-only) wraps the existing `useCountdown(endsAt)` unchanged — no backend or protocol change. Sized with an inline `clamp()` (no existing Tailwind scale reaches projector size, and extending the shared config for one component would be over-engineering); digit-width stability comes from `tabular-nums` plus a fixed `min-width: 3ch` wrapper (the largest configurable question timer is 300s, three digits). Urgency escalates at 10s (a border/icon change — never color alone) and 5s (`motion-safe:animate-pulse`, which already no-ops under `prefers-reduced-motion` at the CSS level, so no JS branch is needed). Rendered instead of the existing compact `TimeRemaining` only when Presentation Mode is active, threaded down as a new `presentationActive` prop through `QuestionHeader`/`HostBilingualQuestion` — normal layouts are byte-for-byte unaffected.

## Winner ceremony

The existing `PodiumReveal` component (built for RFC-014/Phase-2-PR-#5's completion screen) already staged a 3rd→2nd→1st reveal with confetti, Skip, and Replay — extended in place to 5 places (5th → 1st) rather than rebuilt: the staged-reveal list now draws from the top 5 entries instead of the top 3, each card labeled `Winner` (ranks 1–3) or `Runner-up` (ranks 4–5) via a shared `finalResultLabel(rank)` helper also used by the participant's final screen, so the two never disagree. The completed state's visual podium (blocks) and "remaining standings" table are unchanged (still top-3 blocks + rank-4-onward table) — 4th/5th get their moment in the staged reveal, then fold back into the standings table like everyone else. `ReleaseResultsButton` rides `PodiumReveal`'s existing `footer` slot, which only renders once the ceremony has completed or been skipped — so the release action structurally cannot appear mid-ceremony, and it always reflects the *real* `finalResultsReleased` value (refetched, never derived from local ceremony step), so replaying the animation can never re-hide an already-released participant view.

## Realtime and client FSM

One new wire event, `final.results.revealed` (no payload — a pure "go re-read your result" signal), added to the same invalidation set as `session.finished` (session summary refetch). The frontend gains a `FINAL_RESULTS_PENDING` client phase, distinct from `FINISHED`: derived as `state ∈ {FINISHED, ARCHIVED} && !finalResultsReleased`. This is the one piece of client-state surgery in the RFC — splitting one phase into two lets `useParticipantResult`'s phase list simply *exclude* `FINAL_RESULTS_PENDING` (so it never fires a guaranteed-409 request while pending) while `useResults`' (host) phase list *includes* it (so the host's ceremony data loads immediately). No new realtime plumbing was needed for distribution or rank-context — both ride the pre-existing `answer.revealed` invalidation.

---

# Alternatives Considered

**A new `SessionState` (e.g. `FINISHED` → `AWAITING_RELEASE` → `ARCHIVED`)** — rejected: requires a `state` CHECK-constraint migration and an audit of every existing `FINISHED` comparison across the session module (`resultsReadable`, `archive()`'s guard, `SessionResultsQueryService`, etc.), for no benefit the boolean doesn't already provide. The boolean is strictly additive.

**Gating the host's own `/results` read by the release flag too** — rejected: the host needs the full standings *before* releasing, to run the ceremony off them. Gating it would make the ceremony impossible to build without a second, ungated endpoint — worse than the asymmetric gate this RFC actually ships.

**A dedicated `final-results.pending` broadcast** — rejected: `SessionFinishedEvent`/`session.finished` already fires at exactly that moment; a second event for the same transition would be dead weight (the project's own standard: an event without a distinct trigger is a needless broadcast, not a needless-subscriber problem this time, but the same waste).

**A new `question.distribution.available`-style event per reveal** — rejected for the same reason: `AnswerRevealedEvent`/`answer.revealed` already fires at the moment distribution and rank-context become readable; the frontend just widens its existing invalidation set.

**Rebuilding the ceremony as a new component instead of extending `PodiumReveal`** — rejected: the existing component already had the staging engine, Skip/Replay, confetti, and reduced-motion handling exactly right; extending the reveal count from 3 to 5 and adding a label was a small, low-risk change versus reimplementing all of that.

---

# Risks

- `personalResultReadable`'s asymmetry from `resultsReadable` (Proposed Design, Final-results hold) is the one place this RFC touches pre-existing, load-bearing logic. Both are covered by the full-game integration test (`GameplayIntegrationTest.finalResultsAreHeldUntilTheHostExplicitlyReleasesThem`) walking the exact host-unaffected / participant-held sequence.
- `PodiumReveal`'s staged reveal now takes ~1.5s longer per extra place (up to 5 vs. 3) — acceptable for a live-event ceremony; documented via the extended test timeouts, no functional risk.
- The rank-context "nearest tie" simplification (Proposed Design) surfaces only the closest score tie in a rare 3-way tie, not all tied parties — a known, documented simplification, not a defect for the target scale.

---

# Migration

`V10__final_results_hold.sql` — additive: adds `sessions.final_results_released`, backfilling `TRUE` for every pre-existing `FINISHED`/`ARCHIVED` row and defaulting `FALSE` thereafter. No other schema change. Flyway is forward-only throughout this project (no `U` scripts exist anywhere); the documented rollback is a follow-up migration dropping the column, if ever needed.

---

# Open Questions

- **Three-or-more-way score ties in rank-context** — `tiedWith` currently surfaces only the nearest tied neighbour. Revisit if church-scale events start producing frequent multi-way ties (unlikely at current participant counts).
- **Should `answer.distribution.available` and `final-results.pending` exist as dedicated events after all**, e.g. if a future consumer (spectator view, analytics) wants a signal independent of `answer.revealed`/`session.finished`? Deferred until a real second subscriber appears — the project's standing rule against events without a subscriber.

---

# Acceptance Criteria

- [x] `Session.releaseFinalResults()` is idempotent and requires `FINISHED`; `finish()` resets the flag; unit-tested (`SessionTest`).
- [x] Host `/results` is unaffected by the release flag; participant `/participants/{id}/result` is held at `FINISHED`/`ARCHIVED` until release; both directions covered by `SessionResultsQueryServiceTest` and the full-game integration test.
- [x] `GET /answer-distribution` is host-only, phase-gated to post-reveal, counts accepted answers exactly once, and correctly sums multi-select selections above the answered count (`AnswerDistributionQueryServiceTest`, `GameplayIntegrationTest`).
- [x] `GET /participants/{id}/rank-context` is public (unguessable-id gated), phase-gated, and refuses on the quiz's last question unconditionally; first/last/solo/tied cases covered (`ParticipantRankContextQueryServiceTest`, `GameplayIntegrationTest`).
- [x] `POST /results/release` is host-only, idempotent, and publishes `FinalResultsReleasedEvent` → `final.results.revealed` exactly once even across duplicate calls (`ReleaseFinalResultsApplicationServiceTest`).
- [x] `V10__final_results_hold.sql` applies incrementally, backfilling pre-existing sessions as released.
- [x] Frontend: `ProjectorCountdown` renders only in Presentation Mode, escalates urgency without relying on color alone, respects reduced motion, and never shifts surrounding layout as digits change (`ProjectorCountdown.test.tsx`).
- [x] Frontend: per-option counts/percentages render post-reveal and share one count across English/Hindi by construction; `RankNeighbours` renders ahead/behind/tied correctly and never fetches the full leaderboard; `FinalResultsPendingScreen` renders while pending and survives a fresh mount; the five-place ceremony reveals in order with Winner/Runner-up labels; the release button is idempotent and reflects real state (`SessionLivePage.test.tsx`, `PlaySessionPage.test.tsx`).

---

# Future Work

- Question Versioning (PR #47B) — draft revisions, immutable published versions, quiz version pinning, explicit upgrade action. Begins only after this RFC/PR is reviewed and approved.
- Promoting `answer.distribution.available` / `final-results.pending` to real events if a second subscriber (spectator view, analytics) ever needs them independently of `answer.revealed`/`session.finished`.
- Surfacing more than the nearest tied neighbour in `rank-context` if multi-way ties become common.
