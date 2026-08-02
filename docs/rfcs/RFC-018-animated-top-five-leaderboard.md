# RFC-018 The Animated Top 5 and the Final Question's Missing Leaderboard

Status

Implemented

<!-- Draft | Proposed | Accepted | Implemented | Superseded by RFC-XXX
     Implemented — the whole scope shipped in one PR, the same precedent as
     RFC-010/011/013/014/015/016/017. See README.md for the lifecycle. -->

Authors

Aman Minz

Created

2026-08-02

Updated

2026-08-02

---

# Summary

Two presentation problems survived RFC-017. The host's between-questions leaderboard is a static table of *every* participant, which is neither readable from the back of a hall nor interesting to watch; and the quiz's last question still passes through that same leaderboard on its way to the podium, putting the finishing order on the projector moments before the ceremony is supposed to reveal it. This RFC replaces the interim board with an animated Top 5 — scores count up while everyone holds their old position, then the rows travel to their new ranks — and removes the last question's leaderboard step entirely, in the engine rather than only in the host client. Both halves rest on one new host-only projection, `GET /sessions/{id}/leaderboard/top-five`, which returns the two authoritative boards the animation moves between. Nothing about scoring, tie-breaking, ranking, answer distribution, participant rank privacy, the final-results release gate, or the podium changes.

---

# Motivation

The second live event ran on RFC-016/017 and produced two notes from the room. The first: between questions the projector shows a full table of everyone, and with a real congregation on it the interesting part — who just overtook whom — is invisible, because the table simply appears in its new order with no indication that anything moved. A leaderboard nobody can read the *story* of is a leaderboard that costs time and gives nothing back.

The second is sharper. RFC-017 closed the participant-facing half of the final-rank leak: no phone shows its own final rank until the host releases results. But the *host's* screen — the projector the whole room is watching — still displayed an ordinary interim leaderboard after the last question's reveal, because the phase machine required passing through `LEADERBOARD` to reach `advance`. So the finishing order was on the big screen, in full, one host click before the podium ceremony that exists to reveal it. The private half was fixed and the public half was not, which is the wrong way round: the projector is the more exposed of the two.

---

# Goals

- No leaderboard of any kind between the last question's reveal and the podium — enforced by the engine, not by the host client remembering not to ask.
- The between-questions board shows ranks 1–5 only, with no "+N more", no scrolling, and no path to ranks 6 onward before the podium.
- Score increases and rank changes are two separate, legible steps, driven by authoritative before-and-after data rather than a diff of two renders.
- The animation is always skippable and never blocks the host beyond its own duration; reduced motion gets the same information without movement.
- Participants receive none of it.

# Non-Goals

- Any change to `ScoringService`, `ScoringPolicy`, or `LeaderboardService`'s ranking and tie-break *algorithm* — reused exactly as RFC-006 defined it. `LeaderboardService` gains a second entry point; the rule it applies is untouched.
- Any change to the participant experience beyond what RFC-015/017 already established. Participants keep their own result and neighbours after a non-final question, and the same held pending screen after the last one.
- Any change to the podium ceremony, its pacing, its ordering, or the release gate (RFC-015).
- Broadcasting names, scores, or ranks over a shared session event — the projection stays a host-authenticated read.
- Question Versioning (PR #47B) — still deferred.
- Duplicate-name enforcement — explicitly not combined into this work.

---

# Proposed Design

## The last question has no leaderboard step

The rule is stated once, in the engine, in the two places the phase machine could otherwise produce that screen:

- `ShowLeaderboardApplicationService` refuses when `QuestionProgression.nextAfter(quiz, currentQuestionId)` is empty — `session.leaderboard.not-available` (409).
- `AdvanceQuestionApplicationService` accepts an advance from `ANSWER_REVEALED`, but **only** when the quiz is exhausted; a non-final question still has to pass through its standings. Otherwise refusing the leaderboard would simply strand the host in a phase with no legal exit.

Putting the refusal in the application layer rather than the host client is what makes the guarantee hold for a stale tab, a retried request, or a future client — the same reasoning RFC-017 applied to `personalResultReadable`. `QuestionProgression.nextAfter` is deliberately the same call the engine already uses to decide when to finish a session and that RFC-015/017 use to gate rank context and personal results, so "is this the final question" has exactly one definition in the codebase and the frontend never infers it from an index.

The host's flow becomes:

```text
non-final:  QUESTION_OPEN → ANSWER_REVEALED → LEADERBOARD (animated Top 5) → next question
final:      QUESTION_OPEN → ANSWER_REVEALED → (finish) → podium → release
```

## One purpose-built projection, not a slice of the full standings

`TopFiveLeaderboardQueryService` (host only, `GET /sessions/{id}/leaderboard/top-five`) returns the standings **before** the question in play and **after** it, five rows each. It is a separate read rather than a narrowing of `/results` for two reasons: the full read has no notion of a previous board at all, and `/results` must keep returning everything, because the podium is built on it.

Its two gates are borrowed rather than invented: the `ANSWER_REVEALED`/`LEADERBOARD` phase gate is the identical reveal-time gate `AnswerDistributionQueryService` enforces (standings before the reveal leak who answered correctly, ADR-006), and the not-the-last-question gate is the same `nextAfter` check above. A successful read is therefore always a non-final question's, which is why `finalQuestion` is always `false` on the wire — it is there so a client can assert the rule rather than assume it.

### The previous board is a ranking, not arithmetic

`LeaderboardService` gains `rankBefore(participants, roster, questionId)`: the same ranking rule, applied to each participant's answers with that question's answer excluded. Both entry points now run through one private `rank(participants, roster, predicate)`, so there is one comparator and one place ordering is decided.

This matters more than it first appears. Recovering a previous score by subtracting `pointsEarned` from the current one would be arithmetically correct — a participant's cached score *is* the sum of their answers' points, and there is at most one answer per question — but the ranking's tie-break is "who reached that score soonest", and that genuinely differs between the two boards. Two players level on score can be ordered one way before a question and the other way after it purely because one of them answered late. A previous rank recovered by arithmetic would be wrong exactly at a tie, which is exactly where a leaderboard's credibility is tested. `LeaderboardServiceTest` pins that case directly: two players, equal scores throughout, ordered differently on the two boards.

### Absent ranks are the privacy mechanism

Both `previousRank` and `currentRank` are nullable, and each is absent precisely when including it would say something the projector has no business showing:

| Board | Field | Absent when | Client shows |
| --- | --- | --- | --- |
| `currentTopFive` | `previousRank` | they were not in the previous Top 5 | `New Top 5` |
| `previousTopFive` | `currentRank` | they have dropped out of the Top 5 | nothing; the row leaves |

An entrant's old rank and a leaver's new rank are both positions below fifth, so neither crosses the wire at all. That is stronger than the alternative of sending them and asking the client not to render them, and it is what lets the frontend follow the "do not invent movement when previous rank is unavailable" rule without needing a policy of its own: it has nothing to invent from.

## The animation: two steps, and both are transforms

`useTopFiveAnimation` runs `scores → positions → done`. Sequential, not concurrent: a row that moves while its own number is still climbing is unreadable at projector distance, and the point of the sequence is that the room sees *why* the order changed before it changes.

- **`scores`** — every row holds its **previous** slot while its number counts from `previousScore` to `currentScore` over ~1s, eased out. Points earned render as `+650`.
- **`positions`** — rows travel to their authoritative new slots; movement indicators appear.
- **`done`** — settled, and the host's next step unlocks.

Counting uses `setInterval`, not `requestAnimationFrame`: at this duration the visual difference is nil, and timers are what let the tests step through the whole sequence with fake timers instead of waiting out a real second per case. The final numbers are never the animation's: every non-`scores` stage renders `currentScore` verbatim, so the board lands on exactly the server's values regardless of where the easing was.

Movement itself is a **transform on a stable element**, which is the FLIP idea without the library. Each row is absolutely positioned at `translateY(rowHeight × (rank - 1))`, keyed by participant id; a rank change changes the transform and the browser animates the travel. A row therefore never unmounts and remounts somewhere else — the element carrying a name is the same element throughout, which is both what makes the movement readable and what makes React's reconciliation a non-event here. No animation dependency was added; the project has none, and one layout rule plus a CSS transition is not a reason to acquire one.

Row heights are fixed (a CSS custom property, so the projector layout can be taller without touching the arithmetic) because both the slot positioning and the "no scrolling on a projector" requirement depend on knowing exactly how tall the board is.

### Skipping, and what "complete" gates

`Skip Animation` sets the stage to `done` and nothing else: it issues no command, touches no server state, and is simply a local jump to the authoritative final board. It is a real button, keyboard-reachable, and it disappears once there is nothing left to skip.

`useGameHost` holds `isLeaderboardAnimating` and refuses `canAdvance` while it is true, so **Next Question** is disabled until the board settles or the host skips. That flag is true only while there is genuinely an animation to wait for — if the projection is missing or the request failed, the game is never blocked on it, and the page falls back to the plain `LeaderboardTable` off the host's existing `/results` read. The animation is presentation; it is not allowed to become a dependency of running the quiz.

### Recovery, reduced motion, and staleness

Animation state is local display state and nothing else. On refresh or reconnect the host refetches the projection and animates from the same two authoritative boards — which is safe precisely because both are server state, not a remembered render. Under `prefers-reduced-motion` the hook starts at `done`: final scores, final order, and the same movement labels, without count-up or travel.

Staleness is handled twice, deliberately. The query key includes the question id, so a new question cannot render against the previous one's cached boards; and `useGameHost` additionally drops a transition whose `questionId` does not match the question in play, whatever the cache holds. The projection also rides the existing reveal/leaderboard broadcast as its invalidation signal (host-side only, by session prefix), exactly as `useResults` does — no new event type was added, per RFC-005's "events are notifications, not data".

## What participants get

Nothing new. The endpoint requires the hosting identity, so a participant device cannot read it; no shared session event carries names, scores, or ranks; and after the last question participants keep the RFC-017 pending screen with no rank, no neighbours, and no Winner/Runner-up label until the host releases results. The frontend's `LEADERBOARD` handling on the host page also routes the last question to a host-facing "that was the last question" notice rather than any standings, so even a client that somehow lands in that phase shows nothing.

---

# Alternatives Considered

**Reconstructing the previous board by subtracting `pointsEarned` client-side** — rejected. Arithmetically sound for the scores, wrong for the ordering: the tie-break depends on answer times, so the two boards can order equal scores differently. It would also have put a ranking decision in the frontend, which ADR-006 and every other read in this codebase deliberately avoid.

**Returning the full standings and letting the client take the first five** — rejected. The requirement is that ranks 6 onward do not reach the projector before the podium; a client-side `slice` satisfies the pixels and not the wire. The purpose-built projection also carries the previous board, which `/results` has no concept of.

**Sending an entrant's true previous rank (6, 7, …) so the client can render "up 3"** — rejected. It is a rank the board never displayed and, below fifth, one this milestone's whole premise says should not be exposed. "New Top 5" is both truthful and the more legible thing to put on a projector.

**Keeping the last question's `LEADERBOARD` phase and merely hiding the screen in the host client** — rejected, and for the same reason RFC-017 gave for `personalResultReadable`: a rule that only exists in one client is a rule that a stale tab, a retry, or the next client breaks. Refusing in the application layer costs one check and makes the guarantee structural.

**Adding a FLIP or spring animation library** — rejected. One absolutely-positioned row per participant with a CSS transition on `transform` achieves the same result, and the project currently has no animation dependency; acquiring one for a single layout behaviour is not justified.

**Auto-advancing when the animation completes** — rejected. Every transition in this engine is the host's, deliberately (RFC-004); the animation unlocks the button and does not press it.

---

# Risks

- The `clamp()` sizing and the fixed row height were reasoned about rather than measured — jsdom has no layout engine, so the tests can assert structure, ordering, and computed styles but never actual pixel fit at 1366×768 or 1920×1080. The manual checklist remains the real acceptance test for the projector half, exactly as in RFC-017.
- `AdvanceQuestionApplicationService` now accepts two phases instead of one. The extra `next.isPresent()` guard is what keeps that from becoming "the host may skip any leaderboard", and both directions are pinned by integration tests — but it is the one place in this RFC where a future edit could quietly widen a transition.
- Existing sessions that were already sitting at the last question's `LEADERBOARD` phase when this deploys can no longer be advanced by the host client's leaderboard path. They still advance (the `LEADERBOARD → finish` branch is untouched), and the host screen shows the ceremony notice rather than standings; but the host UI for that specific in-flight state is a fallback, not a designed screen.
- The `leaderboard` field on `SessionSnapshotResponse` remains present-and-always-empty (RFC-017's deliberate choice). Unchanged here, still worth removing in a future API-versioning pass.

---

# Migration

None. No database schema changed: both boards are projections over data already stored (`Participant.totalScore` and its answers' `pointsAwarded`), computed per request and never persisted (ADR-006). Every API change is additive — one new endpoint, one new nullable-rank response shape — except the two newly-refused transitions on the last question, which no correct client relies on after this change and which the host client no longer attempts.

---

# Acceptance Criteria

- [x] `ShowLeaderboardApplicationService` refuses on the quiz's last question (`session.leaderboard.not-available`), proven at the HTTP level with real question ordering.
- [x] `AdvanceQuestionApplicationService` finishes the session directly from the last question's `ANSWER_REVEALED`, and refuses that same advance on a non-final question.
- [x] `/leaderboard/top-five` returns exactly five rows per board when the room is larger, fewer when it is smaller, never a sixth, and refuses before the reveal, on the last question, and to anyone but the session's host (401 without a host token).
- [x] An entrant carries no `previousRank` and a leaver no `currentRank`; `previousScore + pointsEarned == currentScore` for every row.
- [x] `LeaderboardService.rankBefore` orders a tie differently from `rank` when the current question's answer times would flip it — the case arithmetic could not have got right.
- [x] Equal scores at distinct backend ranks render at those distinct ranks, backend and frontend.
- [x] Scores begin at the previous authoritative values, end on the exact current ones, and the score step completes before the reorder — asserted with fake timers, not by waiting.
- [x] Skip renders the authoritative final board immediately, enables the host's next action, and issues no request.
- [x] Reduced motion renders the settled board with movement labels intact and no count-up.
- [x] The last question mounts no leaderboard component and requests no Top 5 projection, including on a fresh mount into that phase.
- [x] RFC-015's ceremony/podium tests and RFC-017's privacy tests pass unmodified.

---

# Future Work

- Measure the projector layout at real resolutions during the next live event and tighten the `clamp()` bounds and row height from evidence rather than reasoning.
- A host-configurable animation duration, if a room ever finds ~1.7s of total sequence too slow or too fast — deliberately not a setting yet, since one event's feedback is not a preference.
- Remove the always-empty `leaderboard` field from `SessionSnapshotResponse` (carried over from RFC-017).
- Question Versioning (PR #47B).
