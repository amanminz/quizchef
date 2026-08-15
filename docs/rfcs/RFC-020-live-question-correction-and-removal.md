# RFC-020 Correcting and Removing a Question During a Live Session

Status

Implemented

<!-- Draft | Proposed | Accepted | Implemented | Superseded by RFC-XXX
     Implemented — the whole scope shipped in one PR, the same precedent as
     RFC-010/011/013/014/015/016/017/018/019. See README.md for the lifecycle. -->

Authors

Aman Minz

Created

2026-08-15

Updated

2026-08-15

---

# Summary

A Quiz Master who discovers a bad question mid-event can now fix it or pull it, without
abandoning the session. Both recoveries are **session-scoped**: the Question Library record
is never touched, and another session of the same quiz still asks the question this one
corrected or dropped. The backend is the sole authority for cancelling the affected answers,
reversing the points they earned, and recalculating the sequence — the frontend never
subtracts a score.

---

# Motivation

Published questions are immutable and a session pins the version it executes (RFC-003,
RFC-004). That is exactly right for content integrity — a past session's questions stay the
questions it actually asked — and it leaves a host running a live event with nothing to pull
when a question turns out to be wrong.

The failure modes are ordinary and they all happen in front of a room:

- a typo in the prompt,
- an option worded so badly that the question has two defensible answers,
- and the worst one: the option marked correct is not the correct one.

Until now the only options were to keep playing a question the host knows is broken, or to
abandon the session. Both are worse than the problem.

---

# Goals

- Correct a question mid-session — prompt, option wording, and **which options are correct**
  — in every authored language, without touching the library.
- Remove a question from the session entirely, at any point before it has been scored.
- Reverse every effect of a cancelled attempt: answers, points, totals, leaderboard order,
  answer distribution, answer progress, Top 5 transition boards, result projections.
- Keep the sequence coherent afterwards: no gaps in numbering, no stranded timers, no way
  for a refresh or a reconnect to produce a different order.
- Exactly one authoritative outcome under concurrency — a host double-click, a timer firing
  mid-removal, an answer arriving as points are reversed.

---

# Non Goals

- **Publishing a correction back to the library.** "Save this correction as a new published
  version" is Question Versioning's job (still deferred, PR #47B). This RFC deliberately
  ends at the session boundary.
- **Adding or removing options.** A correction rewords and re-marks the options the question
  already has. Changing the option set is authoring, not recovery — see *Risks*.
- **Correcting a question the room has already played.** Its answers are scored and its
  standings shown; quietly rescoring it would change a leaderboard the room has seen.
  Removal is the honest recovery there, and it is refused too once the question is behind
  the one in play.
- **Editing a finished session.** History is history.

---

# Background

Three existing decisions shape everything below.

**The session already owns things the quiz does not.** RFC-013's per-session shuffle
(`session_question_order`) established the pattern: when a session needs to differ from its
published quiz, the difference belongs to the session. Corrections and removals are the same
shape of problem and get the same shape of answer.

**Scores are a cache of answers.** `Participant.totalScore` is the running sum of that
participant's `ParticipantAnswer` points (ADR-003). Everything else the UI shows — the
leaderboard, the standings, the distribution, the progress counts, the personal result — is
projected from those answers at read time (ADR-006). Nothing is stored downstream.

**Timers are already idempotent.** `OpenQuestionApplicationService.openIfPreviewExpired` and
`CloseQuestionApplicationService.closeIfExpired` both check that the session is still on the
exact question the callback names, in the expected phase, before doing anything.

---

# Proposed Design

## The effective quiz

Thirteen call sites in `session/application` read the quiz as authored. A removal or a
correction must be invisible to every one of them, and they must never disagree with each
other about what question five is.

`SessionQuizQuery` is the single place the difference is applied. It wraps the quiz module's
existing boundaries (`GameplayQuizQuery`, `GameplayQuestionContentQuery`) and returns their
**own view types**, so every caller swapped one method call and nothing else:

```text
GameplayQuizQuery ─┐
                   ├─→ SessionQuizQuery ─→ PlayableQuizView / PlayableQuestionContentView
GameplayQuestion   │        │
    ContentQuery ──┘        ├─ removals: filtered out
                            └─ corrections: overlaid
```

- **Removal is a filter.** A removed question is simply not in the list. `QuestionProgression`
  is unchanged and becomes correct for free: it already derives numbering and "is this the
  last question" from the list it is handed, so `Q1 Q2 Q4 Q5` numbers as `1 2 3 4` with no
  arithmetic anywhere.
- **Correction is an overlay.** A language the host did not correct keeps its authored text,
  and an option they did not reword keeps its own — fixing the English answer key must not
  blank the Hindi. The corrected answer key travels through `PlayableQuizView`, the engine's
  scoring boundary; the corrected wording travels through `PlayableQuestionContentView`, the
  display boundary. Correctness structurally cannot leak into the display view.

`ShuffleQuestionsApplicationService` is the one deliberate exception: it validates a
permutation against the quiz's *full* question set, and removals filter afterwards.

## Domain

**`Session.removedQuestions`** — a list of `RemovedQuestion(questionId, removedAt,
removedFromPhase, cancelledAnswerCount)`, inside the aggregate because removal changes the
sequence the session owns. A **marker, not a deletion**: everything reads past it, and the
audit entry outlives the game (section 12 of the brief preferred exactly this).

`Session.removeQuestion(...)` refuses the last question standing — a session with nothing
left to ask cannot produce a result worth showing — and returns `false` rather than throwing
for an already-removed question, so a double-click converges.

**`Session.cancelCurrentQuestion()`** clears the phase and the running clock but keeps
`currentQuestionId`. That one detail is what makes both recoveries work without a new phase:
the engine still knows where in the sequence it stands, and `previewQuestion` already accepts
a null phase, so a corrected question re-enters its reading period and a removed one is
stepped past — with no change to the existing FSM.

**`Participant.discardAnswerFor(questionId)`** — the exact inverse of `recordAnswer`: drop the
answer, subtract its `pointsAwarded`. Arithmetic on the answers rather than a recomputation
from the quiz, so whatever the scoring rule awarded is precisely what comes back off.

**`SessionQuestionCorrection`** — one session's corrected copy of one question: the corrected
`correctOptionIds`, plus prompts and option texts per language, plus a `revision` counter.

## Application

```text
Correct & Replay                          Remove & Continue
────────────────                          ─────────────────
lock the session row                      lock the session row
validate + save the correction            resolve "what comes next" FIRST
cancel every answer (points reversed)     cancel every answer (points reversed)
cancelCurrentQuestion()                   record the removal marker
startPreview(same question)               cancelCurrentQuestion()
publish question.corrected                startPreview(next) — or finish the session
                                          publish question.removed
```

Resolving `next` **before** recording the removal is load-bearing: once the question is out
of the sequence, "what comes after it" has no answer at all, and the wrong answer would be
"nothing" — finishing a session that still has questions left.

Two components are shared rather than duplicated. `CancelQuestionAttempt` is the whole of the
scoring reversal, and it is deliberately the *only* derived state touched: reversing the
answers reverses every projection over them at once, with nothing left to keep in step.
`SessionFinisher` is the extracted ending — `AdvanceQuestionApplicationService` and the
removal path both use it, so a session still ends in exactly one place and history is still
written exactly once.

Whether a correction replays is the **server's** decision, not a flag the host sends: an
upcoming question is corrected silently and arrives fixed; the question in play is corrected
*and* restarted. There is no sensible third option — a question whose answer key just changed
cannot go on being scored against answers given to the old one.

## Concurrency

The real race is an answer committing while a removal reverses scores. `SubmitAnswer` only
*read* the session, so it bumped no version and could not lose an optimistic check: both
transactions would commit, and the question would be gone from the sequence with its points
still in someone's total.

Fix: `SessionRepository.findAndLockById` (`PESSIMISTIC_WRITE`), used by answer submission,
correction, and removal. Postgres serializes the three on the session row. Submission now
resolves its session id through a projection (`findSessionIdById`) and loads the participant
*after* taking the lock — a participant read before it would be a snapshot from before the
decision the lock exists to make.

The other three races already held: stale timers are guarded by the question-id-and-phase
checks in `openIfPreviewExpired`/`closeIfExpired`, and simultaneous correct-and-remove
serialize on the same row.

## API and realtime

| Endpoint | Notes |
| --- | --- |
| `GET /sessions/{id}/questions` | Host only. The effective sequence with numbers, statuses, and content. |
| `POST /sessions/{id}/questions/{questionId}/correction` | Host only. Replays if the question is in play. |
| `POST /sessions/{id}/questions/{questionId}/removal` | Host only. Idempotent. |

Two additive protocol types, `question.corrected` and `question.removed`, carrying the
question id and nothing else. Clients refetch authoritative state, which is already
phase-gated. The removal event's silence is the stronger case: the room never finished that
question, so its correct option is a **spoiler**, not a reveal.

The question-list read is the one place in the session module where an unrevealed answer key
crosses the wire — a host cannot fix a wrong key without seeing it. It is confined to a single
host-authenticated endpoint, is never broadcast, and the host UI renders it only inside the
correction dialog: the panel's rows show the prompt alone, and the panel is hidden entirely in
Presentation Mode.

## Participant experience

A removed question's replacement enters its reading period immediately, so the removal notice
takes the place of the usual "options shortly" message rather than appearing beside it. It is
rendered in **both** event languages unconditionally — every other screen renders the one the
player chose, but this one interrupts a game to explain why the question vanished, and a
player who misreads that has no way to ask.

---

# Alternatives Considered

**Option A — edit the published question.** Rejected outright. It would change every other
quiz using that question, and it would rewrite what *past* sessions asked, since sessions
pin a version rather than copying content. The bug is live and local; the fix must be too.

**Option B — fork the whole quiz into the session.** A session-owned copy of all content on
creation would make corrections trivial (just edit the copy) and was tempting for that
reason. Rejected: it duplicates every question of every session for a feature used by
almost none of them, it makes "which content did this session run" a question with two
answers, and it quietly turns the session module into a content store. The overlay costs one
indexed lookup per read and keeps the quiz module the only owner of content.

**Option C — subtract points in the frontend.** Fastest to write, and wrong in the way that
matters: the host's screen and the players' screens would disagree the moment one of them
refreshed, and a reconnect would restore the cancelled score. Scoring is server-authoritative
(ADR-006) and reversal is scoring.

**Option D — a `CANCELLED` phase in the session FSM.** Considered for the window between
cancelling an attempt and starting the next one. Rejected as a phase that nobody would ever
observe: the whole recovery is one transaction, so the session is only ever seen before it or
after it. `cancelCurrentQuestion()` clearing the phase to null reuses a state the FSM already
permits.

**Option E — let the host choose whether a correction replays.** Rejected as a choice with
one right answer. Continuing a question whose answer key just changed means scoring new
answers by the new key and old answers by the old one.

---

# Risks

**The option set is fixed.** A correction cannot add or remove an option, because recorded
answers point at those ids: a changed set would make the cancelled attempt and the replayed
one incomparable, and would leave the distribution counting options that no longer exist. A
question needing a new option must be removed, not corrected.

**The row lock serializes answer submission per session.** Every answer in a session now
queues behind that session's row. At `maxParticipants` scale the wait is sub-millisecond, and
it is the only construction where "exactly one authoritative outcome wins" is actually true
rather than merely likely.

**The host's question list carries unrevealed answer keys.** Mitigated by host
authentication, by never rendering keys in the panel rows, by hiding the panel in
Presentation Mode, and by not fetching the read at all until the host expands the panel.

**A corrected prompt drops its explanation.** An explanation written to justify an answer
that is no longer the answer is worse than none, so the overlay withholds it for any language
whose prompt was corrected.

---

# Migration

`V14__live_question_correction_removal.sql` adds `session_removed_questions` and
`session_question_corrections` (with prompt, option-text, and correct-option child tables),
all cascading from `sessions`. Purely additive: an existing session has no rows in either and
behaves exactly as before — `SessionQuizQuery` returns the authored quiz unchanged when both
are empty.

No API removed and no wire type changed. `AdvanceQuestionApplicationService` lost four
constructor dependencies to the `SessionFinisher` extraction, and ten application services
now take `SessionQuizQuery` where they took `GameplayQuizQuery`.

---

# Open Questions

None outstanding. One decision was deliberately taken narrowly and may want revisiting: a
question is correctable only while it is upcoming or in play. If hosts turn out to want a
"rescore question 2" recovery after the fact, that is a different feature with different
leaderboard consequences and should be its own RFC.

---

# Acceptance Criteria

- [x] An upcoming question can be corrected; it arrives fixed when the game reaches it.
- [x] An upcoming question can be removed; numbering and totals close the gap with no gaps.
- [x] The question in play can be removed with zero answers: timer cancelled, next question in
      `QUESTION_PREVIEW`.
- [x] The question in play can be removed after answers: every answer cancelled, every point
      reversed, distribution and progress empty.
- [x] Correcting the question in play cancels its attempt and replays it from a full reading
      period, scored against the corrected key.
- [x] Removing the last question left to play finishes the session into the results ceremony,
      with standings captured and no leaderboard for it.
- [x] Removing the only question left is refused (`session.question.removal-not-allowed`).
- [x] A double removal converges; the second call neither throws nor advances play again.
- [x] A stale preview/close timer cannot reopen a removed question.
- [x] A participant cannot reach the correction, removal, or question-list endpoints; nor can
      a host who does not host the session.
- [x] The Question Library record is byte-identical after a correction, read back through the
      quiz module's own gameplay boundary.
- [x] Realtime carries `question.corrected` / `question.removed` with ids only — no answer key,
      no scores, no standings.
- [x] Participants see the bilingual removal notice, and never the removed question's answer.

---

# Future Work

- **Save a correction as a new published version** — the natural join with Question
  Versioning (PR #47B). A host who fixes a genuinely wrong question should be able to push
  that fix back to the library once, rather than re-fixing it every event.
- **Surface removals in completed-session history.** The audit marker is stored
  (`session_removed_questions`, with the phase and the number of answers cancelled) but no
  history screen reads it yet.
- **A host-side undo window** for a removal made by mis-click, before the next question opens.
  Deliberately out of scope here: an undo has to restore cancelled answers, which is a
  different and much harder guarantee than cancelling them.
