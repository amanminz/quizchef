# RFC-014 Multilingual Live-Event Polish

Status

Implemented

<!-- Draft | Proposed | Accepted | Implemented | Superseded by RFC-XXX
     Implemented — the whole scope shipped in one PR (PR #46), the same
     precedent as RFC-010/011/013. See README.md for the lifecycle. -->

Authors

Aman Minz

Created

2026-07-19

Updated

2026-07-19

---

# Summary

Final polish before the first production quiz: bilingual (English/Hindi) gameplay end to end, the missing question-lifecycle affordances, live answer progress with safe early reveal, and quiz identity on every participant screen. Stability first — no scoring, gameplay-FSM, authentication, transport, or ranking redesigns. Completed-session history is explicitly **deferred to a follow-up PR** (see Non-goals).

# Multilingual gameplay

The multilingual *model* has existed since RFC-003: one Question aggregate owns structure (type, difficulty, options with correctness and identity, tags, default language) while displayable text lives in per-language `QuestionLocalization`/`OptionLocalization`, whole-language-or-absent by invariant. This PR makes it *reachable and used*:

- **Authoring** (frontend): the question editor grows language tabs — the default language plus exactly one translation language (`hi`, or `en` when the default is Hindi). Structure is shared and always visible; prompt, option texts, and explanation localize. A translation is all-or-nothing at the form level (mirroring the domain invariant): any translated field filled requires the prompt and every option text. The correct answer references option **ids**, never translated text. Clearing every translated field removes the localization on the next save.
- **Draft structure changes** (backend): a new atomic `Question.replaceStructure(type, defaultContent, options, defaultOptionTexts)` lets a **draft** change its question type and default language — separately those could leave the aggregate momentarily invalid, so they change together, fully validated. `PUT /questions/{id}` accepts optional `questionType`/`defaultLanguage` (omitted = unchanged, so older clients keep working). Published questions remain immutable. Switching to True/False in the editor replaces options only after explicit confirmation, since it discards option identities and their translations; other type switches preserve them.
- **Participant delivery**: unchanged mechanics — the current-question read carries every authored localization and the client resolves preferred → default → first (`resolveLocalization`), so prompt, options, and explanation always come from *one* localization (no mixed-language questions). The participant's language is chosen at join (now limited to English/हिन्दी by one configurable allowlist, `eventLanguages.ts`), persisted on the Participant row and in the client join record, so refresh and reconnect preserve it.
- **Host projection**: `HostBilingualQuestion` renders English and Hindi together when both exist — projector-sized type, aligned option rows (both texts per option), a subtle "Hindi translation unavailable for this question." notice when missing, and bilingual correct-answer + explanation at the reveal.

# Live answer progress and early reveal

- **Read**: `GET /sessions/{id}/answer-progress` (host-only, same authorization as the roster) returns `{answeredCount, eligibleCount}` for the question in play. Accepted answers count exactly once by construction (a participant holds at most one answer per question); duplicates/rejections never reach storage. *Eligible* = currently connected participants plus anyone whose answer is already in — so answered can never exceed eligible, and a late joiner grows the denominator the moment they connect.
- **Push**: `AnswerSubmittedEvent` now also projects to a session-wide `answer.progress` notification (RFC-005 amended) carrying only the questionId — no participant, no counts. The host re-reads the authoritative counts on each notification (and on roster events); participants ignore it and see only "Answer submitted / Waiting for the host…".
- **Early reveal**: at `N/N` (guarded against `N = 0`) the host's next valid transition is *emphasized, never auto-fired*. The host gains a `Close Question` action during `QUESTION_OPEN` (the existing close command); the timer-vs-host race is settled by the server FSM — the loser's `session.invalid-transition` 409 is treated as benign convergence, guaranteeing exactly one transition. Pending state disables the button against double submission.

# Question lifecycle

- **Detail page**: `/questions/{id}` (new) — the breadcrumb target between the library and the editor (`Question Library > <title> > Edit`, title crumb keyed by the real id), and home of the lifecycle actions.
- **Restore**: `POST /questions/{id}/restore` — ARCHIVED → PUBLISHED on the same aggregate (same id, same quiz references, never a copy); repeats 409 (`question.not-restorable`).
- **Safe delete**: `DELETE /questions/{id}` — one consistent rule: deletable iff no quiz composes it, enforced transactionally (`quiz_questions` count + delete in one transaction), so a direct API request fails exactly like the disabled button (`question.in-use`, message carries the count). `GET /questions/{id}/usage` frames the UI affordance. Confirmation names the question title.

# Quiz identity

- The **participant-safe session summary** (`GET /sessions/{id}`) now carries `quizTitle`, resolved through a new quiz-module boundary (`QuizIdentityQuery` — default-localization title), so every participant screen (waiting, question, submitted, personal result, reconnect, final) shows which quiz this is without ever touching the host-only quiz management API. Command responses keep `quizTitle` null — clients learn it from the read they render from.
- The final participant screen leads with the quiz title above "Quiz complete!", rank, score, and `N players · N questions`; long titles wrap inside a max width. The browser tab reads `<Quiz Title> | QuizChef` while inside a session and restores afterwards.
- Host lobby/details gain **Copy Join Details** (`Join <title>` / `Code: <pin>` / participant URL, blank-line separated) alongside Copy code.

# Non-goals (deferred to PR #46B)

**Completed-session history.** An immutable "what happened" record (questions as displayed, localizations, answers, scores, ranks) requires new tables and a capture step at session finish — a risky schema change days before the event, and the running system already retains sessions/participants/answers after FINISHED for the interim. Deferred to its own PR per the milestone plan; `/sessions/history` routes ship with it.

# Testing

Backend: domain tests for `restore` and `replaceStructure` (type rules re-validated, default-language move, translation pruning), application tests for restore/delete (in-use rejection with count, one rule across states), update (type/language via PUT), and answer progress (counts once, eligibility on connect/disconnect/late join, host-only). Frontend: editor-form mapping (shared ids/correctness, all-or-nothing translation, single-language questions stay valid), breadcrumb id-routing, detail-page restore/delete flows incl. the server-side in-use rejection, host bilingual rendering and missing-translation notice, answer-progress display and close-early emphasis, join-screen allowlist/placeholder, and quiz-title presence during play and on the recovered final screen.
