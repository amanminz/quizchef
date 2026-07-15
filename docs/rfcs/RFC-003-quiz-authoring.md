# RFC-003 Quiz Authoring

Status

Accepted

Authors

Aman Minz

Created

2026-07-15

Updated

2026-07-15

---

# Summary

Defines the Quiz bounded context: the Quiz aggregate (metadata, settings, lifecycle, and composition), the reusable Question aggregate with its typed structural rules, the value objects they embed, the domain events, and the persistence model. The domain is established first (Milestone 3 PR #1) without any API; CRUD endpoints follow in PR #2.

---

# Motivation

Quiz authoring is the heart of the product. Getting the aggregate boundaries right before exposing functionality prevents the most expensive mistake available here: welding questions to quizzes and losing the question bank (PRD roadmap v1.1), import/export, and reuse.

---

# Goals

- A rich, framework-independent quiz domain that enforces its own invariants.
- Questions reusable across quizzes from day one.
- A typed question model that can grow beyond Bible quizzes without redesign.

---

# Non Goals

- Quiz/Question CRUD APIs — PR #2.
- Media upload (RFC-007), gameplay, sessions, scoring (RFC-004/006).
- Bible API integration; references are plain value objects.
- Question versioning — deferred until editing published content demands it.

---

# Proposed Design

## Aggregate boundaries: why Question is separate

**A Question must never belong to exactly one Quiz.** Questions are their own aggregate; a quiz owns only metadata, settings, lifecycle, and an ordered list of question references (`QuizQuestion`: questionId + displayOrder, an element collection inside the Quiz aggregate).

Reasons:

- **Reusability** — the same question appears in many quizzes; the question bank (v1.1) and import/export build on this directly.
- **Independent lifecycle** — editing or improving a question does not require touching every quiz that uses it; deleting a quiz never destroys questions.
- **Aggregate size** — a quiz with 50 questions × options × media would be one giant consistency boundary; separate aggregates keep transactions small.

`QuizQuestion` lives inside the Quiz aggregate rather than being an independently-repositoried entity, deliberately: ordering invariants (unique question per quiz, unique position) can only be enforced by the aggregate root. There is no QuizQuestion repository — composition changes go through `Quiz.addQuestion` / `Quiz.removeQuestion`. The `quiz_questions` table backs the collection with database-level uniqueness as the second line of defense.

## Quiz aggregate

Metadata (title required, description), `ownerIdentity` (an embedded `IdentityReference` — who, never more), `visibility` (PRIVATE default | UNLISTED | PUBLIC), `state` (DRAFT → PUBLISHED → ARCHIVED), embedded `QuizSettings`, and the composition.

Lifecycle invariants, enforced by the aggregate — never by controllers:

- Publishing requires at least one question, and only a DRAFT can publish.
- Published quizzes may gain questions but never lose them (`quiz.questions.locked`, 409) — participants may already rely on the composition.
- Archived quizzes are read-only (`quiz.archived`, 409); archiving is terminal.
- Adding a question twice is a conflict (`quiz.question.duplicate`).
- Display order is assigned by the aggregate (append = max + 1), unique per quiz.

## Question aggregate

Title, prompt, optional explanation (shown after answering, PRD), `questionType`, `difficulty` (EASY/MEDIUM/HARD, reserved as a future scoring multiplier), and three embedded collections: options, Bible references, media references.

**QuestionType is modeled from day one** so the platform can grow beyond BELC without redesigning the aggregate. The type carries the structural rules, revalidated on every option change:

- `SINGLE_CHOICE` — exactly one correct option.
- `MULTIPLE_CHOICE` — one or more correct options.
- `TRUE_FALSE` — exactly two options, exactly one correct.

Plus: at least one option, unique option display order, non-blank option text. Structural violations are `IllegalArgumentException` (invalid construction); PR #2 maps them to 400 at the API boundary.

## Value objects

All records, all JPA embeddables:

- **Option** — id (participants pick option ids during gameplay), text, correct, displayOrder.
- **BibleReference** — book, chapter, verseStart, optional verseEnd (null = single verse), optional translation. No API integration.
- **MediaReference** — id, mediaType (IMAGE/AUDIO/VIDEO), storageKey, optional altText, displayOrder. Only the pointer; uploads are the media module's concern (RFC-007).
- **QuizSettings** — randomizeQuestionOrder, randomizeOptionOrder, questionTimeLimitSeconds (5–300, default 30), showLeaderboardAfterQuestion, showExplanationAfterQuestion. New settings are new components — no redesign.

`IdentityReference` gained `@Embeddable` so aggregates in any module can embed the owner reference; this is the pattern Participant will reuse.

## Domain events

Definitions only, no consumers: `QuizCreatedEvent` (quizId, owner, occurredAt), `QuestionAddedToQuizEvent` (quizId, questionId, occurredAt), `QuizPublishedEvent` (quizId, occurredAt). Published by the application services of PR #2 per ADR-005.

## Persistence

`V3__quiz_domain.sql` (additive, idempotent): `quizzes` (with flattened owner reference and settings columns), `questions`, `quiz_questions` (PK quiz+question, unique quiz+order, FKs both ways), `question_options` / `question_media_references` (unique display order per question), `question_bible_references`. CHECK constraints mirror every enum and range invariant. Repositories: `QuizRepository`, `QuestionRepository` — plain, query methods arrive with the use cases that need them.

## Application layer (PR #2 contract)

No services ship in this PR — empty placeholder classes would violate the no-dead-code rule (same reasoning as `AuthenticationResult`, introduced with its first consumer). PR #2 introduces, with these shapes:

```text
CreateQuizApplicationService.create(CurrentUser, CreateQuizCommand)        → authorize(QUIZ_CREATE) → QuizCreatedEvent
AddQuestionToQuizApplicationService.add(CurrentUser, AddQuestionCommand)   → authorize(QUIZ_EDIT) + ownership → QuestionAddedToQuizEvent
PublishQuizApplicationService.publish(CurrentUser, PublishQuizCommand)     → authorize(QUIZ_EDIT) + ownership → QuizPublishedEvent
```

All receive `CurrentUser` and consult `AuthorizationService`; ownership checks (owner-or-admin) are decided in PR #2.

---

# Alternatives Considered

**Questions owned by Quiz (composition)** — rejected: kills reuse, the question bank, and import/export; bloats the aggregate.

**QuizQuestion as an independent entity with its own repository** — rejected: ordering invariants would be bypassable; composition belongs to the root.

**Separate tables for value objects with entity identity** — rejected: options, references, and settings have no lifecycle of their own; element collections keep the model honest.

**String-typed question kinds** — rejected in favor of the `QuestionType` enum with per-type rules (review decision).

---

# Risks

- Element collections rewrite on change (delete + insert); fine at authoring scale, revisit only if question editing becomes hot.
- `quiz_questions.question_id` has a foreign key, so deleting a question used by any quiz is blocked at the database — question deletion policy is a PR #2/question-bank decision.

---

# Migration

`V3__quiz_domain.sql` is additive; existing data is unaffected.

---

# Open Questions

- Question deletion semantics when referenced by quizzes (block vs. soft-delete) — PR #2.
- Whether editing a published quiz's questions requires versioning — deferred.
- Question ownership/authorship for authorization — PR #2.

---

# Acceptance Criteria

- [x] Quiz, Question, QuizQuestion modeled; questions reusable across quizzes (integration-tested).
- [x] All invariants enforced by aggregates with unit coverage, including the per-type option rules.
- [x] Domain events defined.
- [x] `V3__quiz_domain.sql` applies incrementally on an existing database; Hibernate validates the mapping (Testcontainers).
- [x] Quiz domain is framework-independent (ArchUnit).

---

# Future Work

- PR #2: Quiz CRUD (services above, request/response DTOs, ownership policy).
- Question bank browsing and import/export (v1.1).
- Question versioning if published-content editing demands it.
