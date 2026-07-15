# RFC-003 Quiz Authoring

Status

Implemented

Authors

Aman Minz

Created

2026-07-15

Updated

2026-07-16

---

# Summary

Defines the Quiz bounded context: the Quiz aggregate (settings, lifecycle, localized content, and composition), the reusable Question aggregate with its typed structural rules, the value objects they embed, the domain events, and the persistence model. Content is multilingual from the start (Milestone 3 PR #1.5): structure is language neutral, displayable text lives in per-language localizations, and every aggregate carries a configurable default language. The domain was established first (Milestone 3 PR #1/#1.5); PR #2 adds the author-facing quiz APIs — create, read, update, publish, archive — with owner-only mutations and optimistic locking. Question authoring APIs follow in PR #3.

---

# Motivation

Quiz authoring is the heart of the product. Getting the aggregate boundaries right before exposing functionality prevents the most expensive mistake available here: welding questions to quizzes and losing the question bank (PRD roadmap v1.1), import/export, and reuse.

Internationalization is foundational for the same reason: BELC's congregation spans English, Kannada, Hindi, Tamil, Telugu, and Malayalam speakers. Retrofitting translations after text is welded into aggregate columns would be a schema-and-domain rewrite; introducing it before any API exists costs one migration.

---

# Goals

- A rich, framework-independent quiz domain that enforces its own invariants.
- Questions reusable across quizzes from day one.
- A typed question model that can grow beyond Bible quizzes without redesign.
- Multilingual content as a first-class capability: every participant can experience a quiz in their preferred language, while business logic never touches translated text.

---

# Non Goals

- Quiz/Question CRUD APIs — PR #2.
- Media upload (RFC-007), gameplay, sessions, scoring (RFC-004/006).
- Bible API integration; references are plain value objects.
- Question versioning — deferred until editing published content demands it.
- Translation APIs, automatic translation, and import/export of translations.
- Participant language preference and runtime language switching — the participant belongs to the session module (RFC-004).
- UI localization (labels, buttons, error messages) — a future frontend concern, unrelated to content.

---

# Proposed Design

## Aggregate boundaries: why Question is separate

**A Question must never belong to exactly one Quiz.** Questions are their own aggregate; a quiz owns only settings, lifecycle, localized content, and an ordered list of question references (`QuizQuestion`: questionId + displayOrder, an element collection inside the Quiz aggregate).

Reasons:

- **Reusability** — the same question appears in many quizzes; the question bank (v1.1) and import/export build on this directly.
- **Independent lifecycle** — editing or improving a question does not require touching every quiz that uses it; deleting a quiz never destroys questions.
- **Aggregate size** — a quiz with 50 questions × options × media would be one giant consistency boundary; separate aggregates keep transactions small.

`QuizQuestion` lives inside the Quiz aggregate rather than being an independently-repositoried entity, deliberately: ordering invariants (unique question per quiz, unique position) can only be enforced by the aggregate root. There is no QuizQuestion repository — composition changes go through `Quiz.addQuestion` / `Quiz.removeQuestion`. The `quiz_questions` table backs the collection with database-level uniqueness as the second line of defense.

## Content internationalization

Content and structure are split. **Structure is language neutral**: question types, correctness, ordering, difficulty, settings, scripture and media references never vary by language. **Content is localized**: every piece of displayable text lives in a localization value object keyed by `LanguageCode`, owned by its aggregate root.

```text
Quiz                                         Question
├── defaultLanguage : LanguageCode           ├── defaultLanguage : LanguageCode
├── localizations : [QuizLocalization]       ├── localizations : [QuestionLocalization]
│     languageCode · title · description     │     languageCode · title · prompt · explanation
├── owner, settings, visibility, state       ├── questionType, difficulty
└── questions : [QuizQuestion]               ├── options : [Option]
      questionId · displayOrder              │     id · correct · displayOrder
                                             ├── optionLocalizations : [OptionLocalization]
                                             │     optionId · languageCode · text
                                             └── bibleReferences, mediaReferences  (language neutral)
```

Principles, and why:

- **Business logic operates on ids, never translated text.** Participants answer with option ids; scoring compares ids to `Option.correct`. Correctness lives on `Option` and never on a translation — a wrong translation can never change an answer. Gameplay is completely language independent.
- **Language selection belongs to the Participant, not the Identity.** The same person may host an English quiz on Sunday and play a Kannada one on Wednesday; language is a per-session choice, not an account attribute. The Participant (session module, RFC-004) will carry the preference; this PR only guarantees the content model can serve it.
- **The default language is configurable per aggregate — English is not special.** BELC will choose `en`; another church can author in `kn` or `hi` without any model change. The default is fixed at creation as the language of the initial content, and its localization is the fallback all display resolution ends at.
- **A translation is whole or absent.** A language is present only if it localizes the question text *and* every option. Partial translations cannot exist, so display resolution is binary: use the requested language if present, else the default. No per-field fallback logic anywhere.

Domain rules, enforced by the roots:

- Exactly one localization per language (`localize` replaces; the persistence PK is the second line of defense).
- The default language localization always exists — created with the aggregate, never removable (`quiz.localization.default-required`, 409).
- Option localizations must cover exactly the option set: one text per option, no strays, no duplicates, all in the language being added (violations are `IllegalArgumentException` → 400 in PR #2).
- Localizations cannot exist without their parent aggregate — they are element collections inside it, deleted with it (`ON DELETE CASCADE` as the database mirror).
- `replaceOptions` re-verifies every translation: languages whose texts still cover the new option set survive (texts of dropped options are pruned); a language left incomplete is removed entirely and must be re-translated. Changing what a question asks invalidates its translations — that is a feature, not a loss.

`BibleReference` is deliberately **not** localized: Exodus 3:1 is Exodus 3:1 in every language — the canonical reference is language independent. Only display names of Bible books may ever vary, and that is a rendering concern for later. `MediaReference` is shared across all translations.

## Quiz aggregate

`ownerIdentity` (an embedded `IdentityReference` — who, never more), `defaultLanguage`, localized content (title required, optional description per language), `visibility` (PRIVATE default | UNLISTED | PUBLIC), `state` (DRAFT → PUBLISHED → ARCHIVED), embedded `QuizSettings`, and the composition.

Lifecycle invariants, enforced by the aggregate — never by controllers:

- **Draft-first authoring**: content (localizations) and settings change only while DRAFT (`quiz.content.locked`, 409 otherwise). A published quiz is what participants signed up for; the only thing it may still change is visibility.
- Publishing requires at least one question, and only a DRAFT can publish.
- Published quizzes may gain questions but never lose them (`quiz.questions.locked`, 409) — participants may already rely on the composition. (Composition changes ride the question-authoring APIs of PR #3.)
- Archiving is PUBLISHED → ARCHIVED only (`quiz.not-archivable`, 409 for drafts — a draft is edited or abandoned, not retired). Archived quizzes are read-only (`quiz.archived`, 409), including their localizations; archiving is terminal.
- Adding a question twice is a conflict (`quiz.question.duplicate`).
- Display order is assigned by the aggregate (append = max + 1), unique per quiz.

## Question aggregate

`defaultLanguage`, localized content (title and prompt required, optional explanation — shown after answering, PRD), `questionType`, `difficulty` (EASY/MEDIUM/HARD, reserved as a future scoring multiplier), and four embedded collections: options, option localizations, Bible references, media references.

**QuestionType is modeled from day one** so the platform can grow beyond BELC without redesigning the aggregate. The type carries the structural rules, revalidated on every option change:

- `SINGLE_CHOICE` — exactly one correct option.
- `MULTIPLE_CHOICE` — one or more correct options.
- `TRUE_FALSE` — exactly two options, exactly one correct.

Plus: at least one option, unique option display order, and complete option localization per stored language. Structural violations are `IllegalArgumentException` (invalid construction); PR #2 maps them to 400 at the API boundary.

## Value objects

All records, all JPA embeddables:

- **LanguageCode** — a normalized BCP-47 tag (`en`, `kn`, `en-IN`, `zh-Hant`; pragmatic subset: language, optional script, optional region). The domain never handles raw language strings.
- **Option** — id (participants pick option ids during gameplay), correct, displayOrder. Deliberately language neutral.
- **QuizLocalization** — languageCode, title, description.
- **QuestionLocalization** — languageCode, title, prompt, explanation.
- **OptionLocalization** — optionId, languageCode, text.
- **BibleReference** — book, chapter, verseStart, optional verseEnd (null = single verse), optional translation. No API integration.
- **MediaReference** — id, mediaType (IMAGE/AUDIO/VIDEO), storageKey, optional altText, displayOrder. Only the pointer; uploads are the media module's concern (RFC-007).
- **QuizSettings** — randomizeQuestionOrder, randomizeOptionOrder, questionTimeLimitSeconds (5–300, default 30), showLeaderboardAfterQuestion, showExplanationAfterQuestion. New settings are new components — no redesign.

`IdentityReference` gained `@Embeddable` so aggregates in any module can embed the owner reference; this is the pattern Participant will reuse.

## Domain events

`QuizCreatedEvent` (quizId, owner, occurredAt), `QuestionAddedToQuizEvent` (quizId, questionId, occurredAt), `QuizPublishedEvent` (quizId, occurredAt), `QuizArchivedEvent` (quizId, occurredAt). Created/Published/Archived are published by the application services per ADR-005; `QuestionAddedToQuizEvent` waits for the attach API (PR #3). No consumers yet.

Events reference aggregate ids only and are language independent — no localization events exist, and none are needed yet.

## Persistence

`V3__quiz_domain.sql` (additive, idempotent): `quizzes` (with flattened owner reference and settings columns), `questions`, `quiz_questions` (PK quiz+question, unique quiz+order, FKs both ways), `question_options` / `question_media_references` (unique display order per question), `question_bible_references`. CHECK constraints mirror every enum and range invariant.

`V4__content_i18n.sql` (content move, no data loss): adds `default_language` to `quizzes` and `questions`; creates `quiz_localizations` (PK quiz+language), `question_localizations` (PK question+language), and `option_localizations` (PK question+option+language), all `ON DELETE CASCADE` from their parent; copies every existing title, description, prompt, explanation, and option text into the default-language localization; then drops the old text columns. Aggregate identities are preserved — the migration is proven by an integration test that seeds a V3 database and migrates it. `option_localizations` has no composite FK to `question_options` deliberately: Hibernate rewrites both element collections independently, so option membership is enforced by the Question aggregate.

`V5__optimistic_locking.sql` adds a `version BIGINT` column to every aggregate table (identities, credentials, user_profiles, identity_sessions, quizzes, questions), backing JPA `@Version` on `AuditableEntity`.

Repositories: `QuizRepository`, `QuestionRepository` — plain, query methods arrive with the use cases that need them.

## Application layer (implemented in PR #2)

One application service per operation, per ADR-005 the only place aggregates are mutated, transactions opened, and events published:

```text
CreateQuizApplicationService.create(CurrentUser, CreateQuizCommand)    → authorize(QUIZ_CREATE)             → QuizCreatedEvent
UpdateQuizApplicationService.update(CurrentUser, UpdateQuizCommand)    → authorize(QUIZ_EDIT)  + ownership  → (no event)
PublishQuizApplicationService.publish(CurrentUser, PublishQuizCommand) → authorize(QUIZ_EDIT)  + ownership  → QuizPublishedEvent
ArchiveQuizApplicationService.archive(CurrentUser, ArchiveQuizCommand) → authorize(QUIZ_EDIT)  + ownership  → QuizArchivedEvent
QuizQueryService.quiz(CurrentUser, quizId)                             → authorize(QUIZ_VIEW)  + visibility → (read only)
```

Controllers resolve `CurrentUser` from `CurrentUserProvider` and delegate — zero authorization logic at the transport edge. `AddQuestionToQuizApplicationService` arrives with the attach API in PR #3.

**Publication preconditions.** The aggregate enforces its own rules (DRAFT only, at least one question, default localization by construction). The service adds the single check that crosses aggregate boundaries: every attached question must be localized in the quiz's default language (`quiz.not-publishable`, 409, naming the offending question ids) — otherwise participants falling back to the default would face untranslated questions.

**Ownership model.** The owner is always `CurrentUser.reference()` — never accepted from the client. Mutations are owner-only; a non-owner gets 404 for a private quiz (existence is not disclosed) and 403 (`quiz.ownership.required`) for one it can see. Reads follow visibility: owners always, everyone else only for non-PRIVATE quizzes (UNLISTED is reachable by id; PUBLIC additionally discoverable once listing exists). Admin moderation of foreign quizzes will be a dedicated permission later, not an ownership bypass — nobody inspects roles.

**Optimistic locking.** Every aggregate carries a `@Version` (base entity). Read responses include the version; `PUT` requires it back and a stale value is rejected with 409 `quiz.concurrent-modification` before anything is applied — two authors editing the same draft can never silently overwrite each other. Same-instant races that slip past the check are caught by the JPA version at flush and surface as 409 `conflict.concurrent-modification`.

**Update semantics.** `PUT /quizzes/{id}` treats omitted fields as unchanged; a provided localization list replaces the full set of translations (the aggregate refuses to drop the default language). Domain `IllegalArgumentException`s surface as 400 (`request.invalid`) via the global handler, as planned in PR #1.

## Public API

```text
POST /api/v1/quizzes                  201  create a draft (owner = caller)
GET  /api/v1/quizzes/{quizId}         200  metadata, settings, state, visibility, default language, localizations, ordered question ids, version
PUT  /api/v1/quizzes/{quizId}         200  update visibility / settings / localizations (state-dependent)
POST /api/v1/quizzes/{quizId}/publish 200  DRAFT → PUBLISHED
POST /api/v1/quizzes/{quizId}/archive 200  PUBLISHED → ARCHIVED
```

There is no `DELETE`, deliberately. Published quizzes may have been played — sessions, scores, and history must stay reconstructable, so quizzes are retired by archiving, never destroyed. A hard delete would also cascade into the composition of other data (quiz_questions foreign keys) for no product benefit; drafts someone abandons simply stay drafts. If a true deletion need ever appears (privacy requests), it will be designed deliberately, not shipped as a default endpoint.

The read API returns question ids only — questions are separate aggregates with their own (upcoming) resource; embedding them would couple the read models and bloat every quiz response.

---

# Alternatives Considered

**Questions owned by Quiz (composition)** — rejected: kills reuse, the question bank, and import/export; bloats the aggregate.

**QuizQuestion as an independent entity with its own repository** — rejected: ordering invariants would be bypassable; composition belongs to the root.

**Separate tables for value objects with entity identity** — rejected: options, references, and settings have no lifecycle of their own; element collections keep the model honest.

**String-typed question kinds** — rejected in favor of the `QuestionType` enum with per-type rules (review decision).

**Text columns on the aggregates with a translations side-table** — rejected: two sources of truth for the same text, and "which language is the column?" has no good answer. One shape for all languages, default included, keeps display resolution uniform.

**A fixed platform default language (English)** — rejected: makes English structurally special. The default is a per-aggregate choice; a Kannada-first church is not a variation, it is the same model.

**Localizations as independent aggregates with their own repositories** — rejected: uniqueness per language, default-language presence, and option coverage are exactly the invariants only the parent root can guarantee.

**Partial translations with per-field fallback** — rejected: every consumer would need field-level fallback logic, and a half-translated question shown to a participant is worse than a cleanly fallen-back one.

---

# Risks

- Element collections rewrite on change (delete + insert); fine at authoring scale, revisit only if question editing becomes hot.
- `quiz_questions.question_id` has a foreign key, so deleting a question used by any quiz is blocked at the database — question deletion policy is a PR #2/question-bank decision.
- `replaceOptions` silently drops translations left incomplete by the change. Authoring UX should surface this (PR #2+ returns which languages were invalidated); the domain rule itself is correct.

---

# Migration

`V3__quiz_domain.sql` is additive; existing data is unaffected. `V4__content_i18n.sql` moves content into localization tables with no data loss (integration-tested against a seeded V3 database); existing quizzes and questions keep their identities and become default-language (`en`) localized. `V5__optimistic_locking.sql` is additive; existing rows start at version 0.

---

# Open Questions

- Question deletion semantics when referenced by quizzes (block vs. soft-delete) — PR #3 (quiz deletion is decided: there is none).
- Whether editing a published quiz's questions requires versioning — deferred.
- Question ownership/authorship for authorization — PR #3.
- Localized display names for Bible books (rendering concern) — deferred until a UI needs them.

---

# Acceptance Criteria

- [x] Quiz, Question, QuizQuestion modeled; questions reusable across quizzes (integration-tested).
- [x] All invariants enforced by aggregates with unit coverage, including the per-type option rules.
- [x] Content is multilingual: `LanguageCode` value object, per-language localizations for quiz, question, and option text; structure and gameplay stay language independent.
- [x] Localization invariants enforced by the roots: one per language, default language always present, option coverage complete per language.
- [x] Domain events defined; language independent.
- [x] `V3__quiz_domain.sql` and `V4__content_i18n.sql` apply incrementally on an existing database with no data loss; Hibernate validates the mapping (Testcontainers).
- [x] Quiz domain is framework-independent (ArchUnit).
- [x] Authoring API: create/read/update/publish/archive with the draft-first lifecycle, owner-only mutations from `CurrentUser`, `AuthorizationService` for every operation, and OpenAPI documentation (integration-tested end to end).
- [x] Optimistic locking on every aggregate; stale saves receive 409 instead of overwriting.

---

# Future Work

- PR #3: Question authoring (question CRUD, localization management, attach/detach on quizzes with `QuestionAddedToQuizEvent`, question deletion policy).
- Quiz listing/browsing (owner's quizzes, PUBLIC discovery) — with the first UI that needs it.
- Participant language preference at session join (RFC-004) — the consumer of this model.
- Question bank browsing and import/export (v1.1), including translations.
- Question versioning if published-content editing demands it.
- Localized Bible book display names, translation tooling, and Bible API integration — when demanded.
