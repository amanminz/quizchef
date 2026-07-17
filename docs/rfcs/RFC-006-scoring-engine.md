# RFC-006 Scoring Engine

Status

Implemented

<!-- Draft | Proposed | Accepted | Implemented | Superseded by RFC-XXX
     Implemented — the scoring formula, the leaderboard projection, and the
     answer audit trail ship with the gameplay execution engine (M4 PR #3).
     See README.md for the lifecycle. -->

Authors

Aman Minz

Created

2026-07-14

Updated

2026-07-17

---

# Summary

Defines how QuizChef turns an answer into points and a set of scores into a ranking. Two small, framework-independent domain services own it: **`ScoringService`** computes the points one answer earns from a **`ScoringPolicy`** value object, and **`LeaderboardService`** projects the current standings. Both are server-authoritative (ADR-006) — a client never computes a score or a rank — and both are pure: no clock, no I/O, no persistence, so they are fully deterministic and trivially tested.

Scoring is the value half of the gameplay engine specified in **[RFC-004 Session Engine](RFC-004-session-engine.md)**: RFC-004 owns *when* a question is open, *when* an answer is accepted, and the phase machine that surrounds it; this RFC owns *how many points* that answer is worth and *how* the resulting scores rank. The two ship together in Milestone 4 PR #3.

---

# Motivation

The leaderboard is the game. If a score can be forged or a rank tilted, the whole live experience is worthless — which is exactly why ADR-006 puts the server in sole charge of correctness, timing, and points. That decision only pays off if the scoring rule itself is (a) somewhere a client can never reach, (b) deterministic, so the same answer always earns the same points and tests never flake on a real clock, and (c) swappable, because "how many points is a fast answer worth?" is a product question that will change per audience — a children's event, a practice round, and a tournament want different curves — and it must change without touching the engine that runs the game.

---

# Goals

- A single, documented scoring **formula** with a speed bonus and a difficulty multiplier, that is never negative and never trusts the client for correctness or timing.
- Scoring as a **policy swap**, not an engine change: "Classic", "Kids", "Practice", "Tournament" differ only in constants.
- A **leaderboard** that is always a projection — recomputed from participants' cached scores, never stored — with a **total, deterministic** ordering (no ties left to chance).
- A durable **answer audit trail**: every answer records what was chosen, how fast, and what it earned, so a score is always reconstructable.
- Pure domain services: no clock, no repository, no framework — deterministic and unit-testable in isolation.

---

# Non Goals

- **Question progression, timers, phases, answer acceptance** — the execution engine (RFC-004). This RFC begins once an accepted answer's correctness and response time are known.
- **Streak, combo, or per-round bonuses** — the policy is shaped for them (a new field, not a new branch), but none ship now.
- **Per-session or per-organization policy selection** — a single active policy is wired today; the seam for selecting one is described under Future Work.
- **Analytics, results export, historical leaderboards** — out of scope for Milestone 4 (PRD defers them).

---

# Design

## Two services, both pure

- **`ScoringService`** — `int award(boolean correct, Duration responseTime, Duration questionDuration, Difficulty difficulty, ScoringPolicy policy)`. A pure function of primitives. It does not load a question, read a clock, or know what a Participant is; the caller (`SubmitAnswerApplicationService`, RFC-004) has already determined correctness by comparing the submitted option ids to the question's correct set, and stamped `responseTime` from the shared server `Clock`. Keeping the service free of entities and time is what makes the formula deterministic and exhaustively testable with plain values.
- **`LeaderboardService`** — `List<LeaderboardEntry> rank(List<Participant> participants, List<SessionRosterEntry> roster)`. Reads only the participants' already-cached `totalScore` and their answer timestamps; it computes an ordering, never a score, and never writes anything.

Both are wired as Spring beans in `SessionGameplayConfiguration` while the classes themselves stay Spring-free — the established QuizChef pattern for framework-independent domain services.

## The formula

For one answer:

```text
incorrect                    → 0
correct                      → round( (base + maxSpeedBonus × remainingFraction) × difficultyMultiplier )
remainingFraction            = clamp( 1 − responseMillis / questionDurationMillis , 0 , 1 )
```

- **Speed bonus is linear in time remaining.** A correct answer at the instant the question opens keeps the full `maxSpeedBonus`; one at the buzzer keeps none. `remainingFraction` is clamped to `[0, 1]`, so a late-arriving or clock-skewed answer can never earn *more* than the cap or a negative bonus, and a zero-length question degrades to the base score rather than dividing by zero.
- **Difficulty scales the whole thing**, base and bonus alike, by the policy's per-difficulty multiplier.
- **Never negative.** An incorrect answer is a flat `0` — QuizChef does not punish wrong answers — and the correct branch is floored at `0` for safety.

## `ScoringPolicy` — the tunable constants

A value object holding `basePoints`, `maxSpeedBonus`, and the three difficulty multipliers (`easy`, `medium`, `hard`). Isolating the numbers here is the whole point: a new scoring scheme is a different `ScoringPolicy`, not different engine code, and a future streak bonus is a new field here, not a new branch in orchestration.

**`ScoringPolicy.classic()`** — the default Kahoot-style scheme: `base 500`, `maxSpeedBonus 500`, multipliers `×1.0 / ×1.25 / ×1.5`. So a flat-out-fast correct answer approaches `1000` on an easy question before difficulty, and a difficult question tops out around `1500`; a correct-but-slow hard answer still earns `750`. The constructor rejects negative points.

## Difficulty comes from the question

The multiplier keys off the quiz's authored `Difficulty` (EASY / MEDIUM / HARD, reused from the quiz module) — a property of the content, not of the run — so the same quiz scores identically every time it is played.

## The answer audit trail

Every accepted answer is recorded as a `ParticipantAnswer` (RFC-004's value object) carrying `questionId`, `selectedOptionIds`, `answeredLanguage`, `submittedAt`, `responseTimeMillis`, and `pointsAwarded`. A participant's `totalScore` is the cached SUM of its answers' `pointsAwarded` (ADR-003), maintained on `recordAnswer`. This is the audit trail RFC-006 promised: every score is fully reconstructable from the answers that produced it — what was chosen, how fast, and what it earned — and the cached total keeps the leaderboard an O(n) read instead of a re-summation per rank.

## The leaderboard projection and tie-breaking

`LeaderboardService.rank` orders participants by, in order:

1. **`totalScore` descending** — the score itself;
2. **earliest most-recent-answer time** — whoever reached their score sooner ranks higher (a participant with no answers sorts to the far future, i.e. last at a given score);
3. **join order** — the final, guaranteed-unique tiebreak.

The three keys make the ordering **total and deterministic**: no two participants ever tie in a way the sort leaves to chance, so the same standings render identically on every client and in every test. Each row becomes a `LeaderboardEntry { participantId, displayName, score, rank }` with `rank` the 1-based position.

The leaderboard is **always computed, never persisted** (ADR-006). It is projected on demand — when the host shows it (`ShowLeaderboardApplicationService`) and inside the reconnection snapshot (RFC-004 replay) — from the participants' cached scores. There is no leaderboard table to fall out of sync.

## Determinism and the clock

Response time is `now − timer.startedAt`, where `now` is the shared injected `Clock` (ADR-005, ADR-006) — never `System.currentTimeMillis()`. The scoring and leaderboard services themselves take no clock at all: time enters only as the already-measured `Duration` and the answers' timestamps. So gameplay scoring is reproducible and the tests are not flaky by construction, not by luck.

---

# Alternatives Considered

**Formula baked into `ScoringService`** — rejected: it welds a product decision (the points curve) to engine code, so every scheme variant becomes an engine change and a test rewrite. The `ScoringPolicy` value object makes a variant a constant swap.

**A stored / incrementally-updated leaderboard table** — rejected: a second source of truth for a number already cached on each participant, and one that can drift, need invalidation, and disagree with the replay snapshot. Projection from cached scores is cheap at session scale and cannot desync (ADR-006).

**Uncached score (re-summed per rank)** — rejected: it turns every leaderboard into an N×answers computation and splits the score's home. The cached `totalScore` on the Participant (ADR-003) keeps ranking an O(n) read.

**Penalising wrong answers (negative points)** — rejected: punitive, surprising in a party game, and against the PRD's tone. Incorrect is a flat zero; the floor guarantees non-negativity regardless of policy constants.

**Step-function speed bonus (buckets)** — rejected for v1: a linear ramp is simpler, continuous, and has no cliff edges where a millisecond changes a bucket. A policy could express buckets later if a product wants them.

**Correctness / timing computed client-side and trusted** — rejected outright by ADR-006. The client renders and submits; the server decides.

---

# Risks

- **Integer rounding at the boundaries.** `round` on the scaled double is deterministic but means two answers milliseconds apart can score identically; the leaderboard's time and join-order tiebreaks resolve the resulting rank ties, so this never produces a non-deterministic standing.
- **A single global policy today.** Every session scores by `classic()`. Selecting a policy per session/organization is future work; until then a scheme change is a redeploy, which is fine at the current scale.
- **Response time depends on a correct server timer.** The measurement is only as good as `timer.startedAt`; since both the timer and the stamp come from the same server `Clock`, they cannot disagree, but a wrong policy duration would mis-scale the bonus — covered by the execution-engine tests in RFC-004.

---

# Acceptance Criteria

- [x] `ScoringService` computes points as documented: incorrect → 0; correct → `round((base + maxSpeedBonus × remainingFraction) × difficultyMultiplier)`, `remainingFraction` clamped to `[0, 1]`; never negative; a zero-length question degrades to base (unit-tested across correctness, speed, difficulty, and edge inputs).
- [x] `ScoringPolicy` isolates base / speed-bonus / difficulty constants; `classic()` is the default; negative constants rejected.
- [x] Scoring is framework-independent (no clock, no I/O, no Spring) — enforced by ArchUnit and provable by the pure unit tests.
- [x] `LeaderboardService` orders by score desc, then earliest most-recent-answer, then join order — a total, deterministic ranking (unit-tested, including ties and no-answer participants).
- [x] The leaderboard is projected from cached scores and never persisted; it is identical when shown live and when replayed in the reconnection snapshot (integration-tested end to end).
- [x] Every answer records `pointsAwarded` alongside its choice and response time; `totalScore` is the cached sum (ADR-003) and is verified in the persisted participants after a full game.

---

# Future Work

- **Per-session / per-organization policy** — `ScoringPolicy` is already a value object and `ScoringService` already takes it as a parameter; selecting one (a "Kids" / "Practice" / "Tournament" scheme) is a resolution step in orchestration, not an engine change. The single `classic()` bean is the only thing standing in for it.
- **Streak / combo bonuses** — a new field on `ScoringPolicy` and a new term in the formula, without disturbing the leaderboard or orchestration.
- **Historical / cross-session leaderboards and results export** — a separate concern (analytics), deferred by the PRD; the durable answer audit trail is the data it will read.
