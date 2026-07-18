# RFC-013 Question Authoring and Quiz Composition

Status

Implemented

<!-- Draft | Proposed | Accepted | Implemented | Superseded by RFC-XXX
     Implemented — the whole scope shipped in one PR (Phase 3 PR #4A), the
     same precedent as RFC-010/011. See README.md for the lifecycle. -->

Authors

Aman Minz

Created

2026-07-18

Updated

2026-07-18

---

# Summary

Completes the host's primary journey by making question authoring **reachable**. The backend question library (RFC-003) has been feature-complete since Phase 2 — create, read, update, publish, archive, plus quiz attach/detach/reorder — and the frontend API module mirrored the full CRUD surface, but no route, page, or button ever invoked the write side. A first-time host hit a dead end: the quiz Questions step showed an empty library with no way to create a question. This PR adds the missing frontend workflow — a question editor, a standalone Question Library page, and quiz-launched authoring with automatic attachment — **against the existing APIs, with zero backend changes**.

RFC-012 is reserved for Performance & Scalability (Phase 3 PR #4, planned but paused for this fix); this document deliberately contains none of that scope.

---

# Motivation

Manual host testing of the primary journey found:

```text
Create Quiz → Metadata → Questions → Empty Question Library → dead end
```

The Questions step's empty state said questions must exist to be attached, but offered no path to create one. `questionApi.create/update/publish/archive` existed since Phase 2 PR #2 with the explicit note "question creation/editing is out of scope for the authoring UI … mirrored here for the module that owns question authoring later." This is that module.

---

# The domain, as it actually is (audited, not invented)

Everything below was read from the code before any UI was written; the UI follows it rather than the other way round.

**Question lifecycle** — `DRAFT → PUBLISHED → ARCHIVED`:

- **DRAFT**: fully editable (content, options, difficulty, localizations, tags). Type and default language are fixed at creation.
- **PUBLISHED**: immutable — quizzes may rely on it. There is no revision/versioning workflow; changing published content means archiving and authoring anew.
- **ARCHIVED**: terminal; unavailable for new quizzes while already-published quizzes keep working. Only published questions can be archived (drafts are edited or abandoned).

**Attachability** — drafts **are** attachable (`AddQuestionToQuizApplicationService`): an author composes a quiz while its questions are still being refined, exactly as the quiz itself stays DRAFT while assembled. Only ARCHIVED is rejected (`quiz.question.not-attachable`). Quiz publish is where the cross-aggregate check happens: every attached question must carry the quiz's default language. An earlier frontend empty-state text claimed "publish a question to make it attachable here" — that was wrong about the domain and is corrected in this PR.

**Composition** — owned by the Quiz aggregate and always backend-persisted: attach appends at `max+1` display order; detach and reorder are draft-quiz-only; duplicates are rejected (`quiz.question.duplicate`); a quiz needs ≥ 1 question to publish. The frontend already persisted all three through optimistic mutations that reconcile to the server (`useQuestionSelection`) — no frontend-only selection list existed or was added.

**Ownership and permissions** — questions are visible to and editable by their owner only (another owner's question 404s, never 403s); there is no cross-author or shared library yet. `QUIZ_CREATE` guards creation, `QUIZ_EDIT` guards update/publish/archive and composition, `QUIZ_VIEW` guards reads. Frontend permission checks remain cosmetic (RFC-009); the backend authorizes every mutation.

**Update contract** — `PUT /questions/{id}` is a true full replacement carrying the last-read `version` (stale → 409). Options keep their ids so translations survive; new options carry fresh client-generated ids. Localizations are all-or-nothing per language (a language's option texts must cover every option); the editor edits only the default language and carries other localizations over verbatim, dropping any left incomplete by an option change — mirroring the domain's own pruning in `replaceOptions`.

---

# Design

## Routes and navigation

```text
/questions                     Question Library (browse, filter, publish, archive)
/questions/new                 create — standalone or quiz-launched (?quizId=…)
/questions/:questionId/edit    edit a draft; published/archived render an explanation instead
```

Sidebar gains **Question Library** between Quizzes and Sessions, shown with `QUIZ_CREATE` (cosmetic, like every nav entry). Breadcrumbs resolve `/questions` → "Question Library", `new` → "New Question", and a question id → its default-language title.

**Return context is an id, not a URL.** Quiz-launched authoring carries `?quizId=<id>`; only plain-slug ids are honored and the return path is always built from the internal route template (`/quizzes/{id}/questions`) — no free-form `returnTo` URL to validate or get wrong.

## One editor, two entry points

`features/questions/components/QuestionEditor` is the single form for create and edit, standalone and quiz-launched. It handles: title, prompt, explanation, type (single choice / multiple answers / true-false), difficulty, default language, options with correctness, and tags. Per-type validation mirrors the backend for fast feedback (single: exactly one correct; multiple: at least one; true/false: exactly two options, one correct — prefilled and add/remove-locked); the server stays the authority for both actions, since a draft must already be structurally valid. Type and language lock when editing (the update contract has no type field; the default language is fixed at creation).

Not in the editor, deliberately: Bible references and media references (no authoring UI yet — carried through updates verbatim), non-default localizations (translation UI is future work; existing translations survive edits per the pruning rule above). The domain supports them; this PR ships the blocking journey, not every field.

## The journeys

- **Create from a quiz**: Questions step → `+ New Question` (also the empty state's `Create Question`) → author → **Publish** → auto-attach to that quiz (a real backend mutation that must succeed first) → return to the same Questions step with "Question published and added to this quiz." and the composition refetched. **Save draft** instead lands on the draft's edit page, quiz context preserved; the draft is also visible (and attachable) in the picker.
- **Partial success is reported honestly**: publish succeeded but attach failed → return with "Question published, but it could not be added to this quiz. You can select it from the library and try again." — the question is in the library, never claimed as selected. Publish failed after create → the draft exists; the user lands on its edit page with the failure stated, values preserved server-side.
- **Standalone**: Question Library → create/edit/publish/archive without any quiz. Archive confirms first (it's terminal).
- **Cancel** returns to wherever authoring was launched from, confirming when the form is dirty; no mutations occur.

These notices travel in router state, never the URL — a refresh doesn't replay them into permanence.

## Composition-step fixes

- The selected panel and the library no longer share one empty-state text: "No questions selected …" vs. "Your question library is empty …" (the actionable one).
- A filtered miss ("No questions match your filters" + Clear filters) is distinct from a truly empty library.
- Draft rows show a DRAFT badge and an "Edit draft" link but stay attachable (the domain's policy); archived rows are disabled with the reason.
- `Next: Review` is disabled at zero questions with visible guidance ("Add at least one question to continue.") — previewing the server's own ≥ 1-question publish rule, which still enforces it.

## State ownership (unchanged rules, new tenant)

`features/questions/` is the fifth feature module and owns `questionKeys` and the question query/mutation hooks (moved from `features/quizzes/`, whose picker was the only consumer before authoring existed). TanStack Query owns all server state; authoring mutations are **never optimistic** (lifecycle transitions are consequential — same line RFC-009 drew for quizzes) and reconcile detail + library caches on success. Composition stays in `features/quizzes/useQuestionSelection`, optimistic as before — it mutates the quiz aggregate, not the question. No global store was added; no realtime events exist or are needed for an author's own mutations.

---

# Testing

Frontend (vitest + MSW against the real route tree): the empty-library dead end is gone (both CTAs, distinct copy, review guard); create-from-quiz publishes, attaches (asserting both mutations), and renders the selected question; partial success shows the honest recovery message with the question visible but unselected; drafts save and continue editing with quiz context; cancel returns without mutations; edit sends a full PUT preserving option ids and the read version; published questions refuse editing with an explanation; library filters/clears/publishes/archives; authoring controls and navigation hide without `QUIZ_CREATE`.

Backend: unchanged, and already covered — `QuestionLibraryIntegrationTest` (full authoring workflow, ownership, stale versions, authorization) and `QuizCompositionIntegrationTest` (draft/published attachable, archived not, duplicates, ownership, reorder set-equality, publish preconditions).

---

# Accepted limitations

- No Bible/media reference or translation editing UI (fields carried through, never lost).
- No library pagination controls yet (first page of 20 by `updatedAt` desc; same "fine at authoring scale" call as RFC-003/009).
- Discard-confirmation uses `window.confirm`; a modal can replace it whenever a design pass wants to.
- Return notices are plain status banners; a toast system remains unbuilt on purpose.
- Full WCAG work stays with the accessibility milestone; this PR keeps the baseline (real buttons, labeled controls, field-associated errors, keyboard-reachable create/select/remove).

# Out of scope

Everything Phase 3 PR #4 (Performance & Scalability, RFC-012) plans; CSV import; AI generation; bulk authoring; collaborative editing; analytics; shared organization libraries; new question types; media upload.
