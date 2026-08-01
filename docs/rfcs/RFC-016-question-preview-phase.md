# RFC-016 Question Reading Period Before Options

Status

Implemented

<!-- Draft | Proposed | Accepted | Implemented | Superseded by RFC-XXX
     Implemented — the whole scope shipped in one PR, the same precedent as
     RFC-010/011/013/014/015. See README.md for the lifecycle. -->

Authors

Aman Minz

Created

2026-08-01

Updated

2026-08-01

---

# Summary

Between the first production event and PR #47A's live-results work, the remaining gap was pacing: a question and its options appear together, with the answer timer already running, so participants have no dedicated moment to actually read the question before the clock is against them. This RFC inserts a short, server-authoritative reading period — `QUESTION_PREVIEW` — at the front of the existing gameplay loop: `QUESTION_PREVIEW → QUESTION_OPEN → QUESTION_CLOSED → ANSWER_REVEALED → LEADERBOARD`. The transition out of it is fully automatic (the same scheduled-timer mechanism that already auto-closes an open question now also auto-opens a previewed one) — no host command can shorten or skip it, and no participant can submit through it.

---

# Motivation

Kahoot-style live quizzes universally separate "read the question" from "the clock is running" — combining them, as the pre-existing flow did, penalizes slower readers and rewards quick pattern-matching over actually engaging with the question. The fix has to be backend-authoritative for the same reason every other gameplay guarantee in this codebase is (ADR-006): a `setTimeout`-only frontend delay would drift between devices, could be bypassed by a direct API call, and would need to be reimplemented identically in three places (host, participant, projector) instead of once.

---

# Goals

- A visible, server-timed reading period before a question's options and answer timer become available.
- Zero ability to submit an answer, see options, or see host reveal/close/leaderboard controls during that period, enforced server-side.
- The existing answer duration stays exactly as configured — the reading period is additional time, never subtracted from it.
- Works identically for the first question and every subsequent one, and survives refresh/reconnect exactly like every other phase does.

# Non-Goals

- A host "skip reading time" control — the spec is explicit that a uniform period for everyone is the safer first implementation; not built.
- Quiz-level (per-quiz or per-question) configuration of the reading period — a single global constant today, deliberately shaped so it *could* become quiz-level later without a redesign.
- Any change to scoring, ranking, answer distribution, or the PR #47A final-results hold/ceremony — all untouched.
- Question Versioning (PR #47B) — still deferred, unaffected by this insert.

---

# Proposed Design

## One new phase, not a parallel state machine

`SessionPhase` gains `QUESTION_PREVIEW`, ordered first: `(IN_PROGRESS, no phase)/LEADERBOARD → QUESTION_PREVIEW → QUESTION_OPEN → QUESTION_CLOSED → ANSWER_REVEALED → LEADERBOARD → …`. `V11__question_preview_phase.sql` widens the `sessions.current_phase` CHECK constraint the same additive way V8 did when the real gameplay phases replaced the placeholder set — no data migration, since the new value is purely permissive.

`Session.openQuestion(UUID, QuestionTimer)` splits into two aggregate methods matching the two-step entry:

- `previewQuestion(UUID questionId, QuestionTimer previewTimer)` — from no-phase/`LEADERBOARD`, sets the question current and starts the reading clock. `acceptsAnswersFor` stays false (it only returns true for `QUESTION_OPEN`), so this method alone is what makes the FSM correctly reject answers, host closes, reveals, and leaderboard transitions during preview — *for free*, since every other aggregate method already guards on a specific phase and none of them list `QUESTION_PREVIEW`.
- `openQuestion(QuestionTimer answerTimer)` (new single-argument overload) — requires `QUESTION_PREVIEW`, keeps the question id unchanged, replaces the timer, flips to `QUESTION_OPEN`.

**One reused timer field, not two.** `Session.currentQuestionTimer` (and its existing `current_timer_started_at/duration_seconds/ends_at` columns) now represents "whichever countdown the current phase is running" — the reading clock during `QUESTION_PREVIEW`, the answer clock during `QUESTION_OPEN` — rather than always meaning the answer clock. This is safe precisely because every existing reader (`CurrentQuestionQueryService`, `SessionSnapshotAssembler`, `SubmitAnswerApplicationService`) already phase-gates before reading it; none of them assumed a global "this field always means the answer window" invariant that reuse would violate. Two parallel timer fields were considered and rejected (Alternatives) — they are temporally exclusive, so a second field would only ever hold stale data or null.

## The automatic transition

`QuestionTimerScheduler` (the port `QuestionOpener` already used to arm the answer-close timer) gains a second method, `schedulePreviewEnd(sessionId, questionId, previewEndsAt, answerDurationSeconds)`, implemented by the same `SchedulingQuestionTimerScheduler` against the same `gameplayTaskScheduler` bean — literally the project's existing scheduled-question-transition mechanism, per the spec's own instruction, rather than a second scheduling concept. The callback is `OpenQuestionApplicationService.openIfPreviewExpired(sessionId, questionId, answerDurationSeconds)`, which mirrors `CloseQuestionApplicationService.closeIfExpired` exactly: idempotent, a no-op unless the session is still `QUESTION_PREVIEW` on that exact question. Unlike close-vs-host-close, there is no competing host-triggered path here at all (the spec explicitly forbids a "skip reading time" browser command in this milestone), so the guard exists purely to make a duplicate or stale-after-the-fact firing harmless, never to settle a real race.

**A circular Spring bean dependency, and how it's avoided.** The natural first design had `OpenQuestionApplicationService` call `timerScheduler.scheduleClose(...)` itself once it opened the question — but `SchedulingQuestionTimerScheduler` (the sole `QuestionTimerScheduler` implementation) already depends on `OpenQuestionApplicationService` to invoke it when the preview timer fires, so that would be a cycle Spring's context refuses to start. `OpenQuestionApplicationService` instead returns `Optional<Instant>` (the answer window, or empty if it was a no-op), and `SchedulingQuestionTimerScheduler`'s own callback chains `scheduleClose(...)` itself using that return value. The application service does pure domain-transition work; the infrastructure scheduler — which already owns both scheduling operations — decides what to arm next.

**Known, accepted limitation, matching existing precedent exactly.** Like the pre-existing answer-close timer, this scheduled task lives only in the in-memory `ThreadPoolTaskScheduler` and does not survive a backend restart; no re-arm-on-startup mechanism exists anywhere in this codebase for either timer. This is not a new gap introduced here — it is the same characteristic the close-timer has always had, and the spec's actual testable requirement ("restart/recovery respects persisted timestamps/state") is about the *read* side: `currentPhase`/`currentQuestionTimer` are persisted, so a session summary or reconnect snapshot always reports the true phase and correct remaining time after a restart, even though the auto-transition itself would need a host to notice and intervene in the (rare, church-scale) event of a mid-preview restart.

## `question.started` corrected, one genuinely new event added

No new wire event was needed for "options opened": `QuestionStartedEvent`/`question.started` already meant exactly that, and previously fired one step too early (at the old single-step open). Moving its publish point to the preview→open transition is a correction, not a new concept — `AdvanceQuestionApplicationService`/`StartQuestionApplicationService` no longer publish it at all; `OpenQuestionApplicationService` does, at the moment options actually become available. The one new event is `QuestionPreviewStartedEvent` → wire type `question.preview.started`, reusing the exact same payload shape (`questionId`, `endsAt`, `durationSeconds`) `question.started` already had, for the earlier moment.

## Options are genuinely absent, not hidden

`CurrentQuestionQueryService` gains `withoutOptions(content)`, mirroring the existing `withoutExplanations(content)` pattern exactly: applied whenever `phase == QUESTION_PREVIEW`, it empties `options` and every localization's `optionTexts`, leaving `prompt` untouched. This is the same public `GET /questions/current` read every device already shares — host and participant both get the stripping for free, with no separate host-only code path to keep in sync, and the host's bilingual projection (`HostBilingualQuestion`) needed only a `previewing` prop to skip rendering the (now-empty) options block and show a "Read the question" notice instead, rather than a new component duplicating its bilingual-resolution logic. `AnswerProgressQueryService` — the one read that was *not* already phase-gated at all — gained an explicit `QUESTION_PREVIEW` exclusion, since "0 / N answered" is a meaningless read before answering is even possible; every other phase (`QUESTION_OPEN`, `QUESTION_CLOSED`, ...) is unaffected.

`endsAt`/`remainingMillis` on the response are reused generically as "the countdown for whatever phase is currently running," matching how the frontend's `useCountdown`/`ProjectorCountdown`/`QuestionTimer` already treat them — none of them ever cared what the countdown *meant*, only that it targets a server timestamp. `durationSeconds` is untouched: it already reflects the quiz's configured *answer* duration unconditionally (from the quiz's settings, not the session's timer), which stays useful context to show during preview too.

## Configuration

`GameplayProperties` (`quizchef.gameplay.question-preview-seconds`, default `5`) is a `@ConfigurationProperties` record registered on the existing `SessionGameplayConfiguration` — the same `CorsProperties`/`RateLimitProperties`/`JwtProperties` idiom already used three times in this codebase, under its established `quizchef.*` prefix (not the spec's illustrative `quiz.gameplay.*`, which doesn't match anything else here). Deliberately global, not quiz-level: `QuizSettings` is untouched, per the spec's explicit instruction not to add quiz-level configuration for this milestone.

---

# Alternatives Considered

**Two timer fields (`currentPreviewTimer` alongside `currentQuestionTimer`)** — rejected: the two are never simultaneously meaningful, so a second field only ever holds a stale or null value the other one didn't already communicate via phase. One field, reused, is strictly simpler and needs no migration.

**A host-triggered "skip reading time" command** — rejected for this milestone, exactly as the spec asked: a uniform period is the safer first cut, and adding a command now would mean a second path into `QUESTION_OPEN` to keep race-safe against the scheduled one, complexity this PR doesn't need yet. Recorded in Future Work.

**Quiz-level preview duration (a new `QuizSettings` field)** — rejected for now: the spec explicitly asked not to add quiz-level configuration unless it already fit the existing model, and it doesn't yet — `GameplayProperties` is deliberately the seam this would extend from later.

**`OpenQuestionApplicationService` arming its own close timer** — rejected: creates a circular Spring bean dependency with `SchedulingQuestionTimerScheduler` (see Proposed Design). Returning the answer window and letting the scheduler chain the two steps avoids the cycle entirely.

---

# Risks

- The circular-dependency-avoidance design (application service returns data, scheduler decides what to arm next) is the one piece of infrastructure wiring in this RFC that isn't a straightforward mirror of existing code — flagged explicitly in both the class Javadoc and this RFC so a future refactor doesn't "simplify" it back into a cycle.
- Reusing one timer field for two meanings is safe only as long as every future reader remembers to phase-gate before interpreting it — the same discipline already required for `correctOptionIds`/explanation-stripping, not a new kind of risk.
- No restart re-arm mechanism (see Proposed Design) — an accepted, pre-existing characteristic, not newly introduced.

---

# Migration

`V11__question_preview_phase.sql` — additive: widens the `sessions.current_phase` CHECK constraint to permit `QUESTION_PREVIEW`. No data migration; no existing session could have been in a phase the widened constraint doesn't already allow. Flyway is forward-only throughout this project; the documented rollback is a follow-up migration narrowing the constraint back, if ever needed.

---

# Open Questions

- **Host "skip reading time"** — deferred per the spec's explicit guidance; revisit if live events show the fixed period is a real pacing problem rather than a helpful one.
- **Per-quiz reading-period configuration** — `GameplayProperties` is the seam; promoting it to a `QuizSettings` field is a small, deliberate follow-up once (if) there's a real product need, not a redesign.

---

# Acceptance Criteria

- [x] `Session.previewQuestion`/`openQuestion(QuestionTimer)` enforce the phase guards described above; `SessionExecutionTest` covers the full lifecycle plus the new preview-specific illegal-transition cases.
- [x] `SubmitAnswerApplicationService`, `CloseQuestionApplicationService`, `RevealAnswerApplicationService`, `ShowLeaderboardApplicationService` all correctly refuse during `QUESTION_PREVIEW` with zero code changes to those services (verified in `GameplayIntegrationTest`).
- [x] `AnswerProgressQueryService` explicitly refuses during `QUESTION_PREVIEW`; every other phase unaffected (`AnswerProgressQueryServiceTest`).
- [x] `CurrentQuestionQueryService` strips options/optionTexts during preview and restores them once open; `OpenQuestionApplicationServiceTest` covers the idempotent auto-transition (opens once, no-ops on duplicate/stale firing, no-op for an unknown session).
- [x] `V11__question_preview_phase.sql` applies incrementally with no data migration.
- [x] `GameplayIntegrationTest` walks a real question through `QUESTION_PREVIEW → QUESTION_OPEN` via the actual scheduled timer (a 1-second test-only override, never a fake clock), asserting no options/rejected submission during preview and the full configured answer duration once open.
- [x] Frontend: a new `QUESTION_PREVIEW` client phase renders the reading period on both host (`HostBilingualQuestion`'s `previewing` prop) and participant (`QuestionPreviewNotice`) screens, with no options, no answer-progress badge, and no host action button; the transition to `QUESTION_OPEN` is driven only by the `question.started`/`question.preview.started` realtime invalidation, never a local timer (`SessionLivePage.test.tsx`, `PlaySessionPage.test.tsx`).

---

# Future Work

- A host "skip reading time" control, if live events show the fixed period is a real pacing problem.
- Per-quiz or per-question reading-period configuration, extending `GameplayProperties`' seam.
- Question Versioning (PR #47B) — begins only after this PR is reviewed.
