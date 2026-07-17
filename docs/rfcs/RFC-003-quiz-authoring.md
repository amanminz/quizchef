# RFC-003 Quiz Authoring

Status

Implemented

Authors

Aman Minz

Created

2026-07-15

Updated

2026-07-17

---

# Summary

Defines the Quiz bounded context: the Quiz aggregate (settings, lifecycle, localized content, and composition), the reusable Question aggregate with its typed structural rules and its own lifecycle, the Tag aggregate, the value objects they embed, the domain events, and the persistence model. Content is multilingual from the start (Milestone 3 PR #1.5): structure is language neutral, displayable text lives in per-language localizations, and every aggregate carries a configurable default language.

Built in five steps: the domain first (PR #1/#1.5, no API), then the author-facing quiz APIs (PR #2 — create, read, update, publish, archive, owner-only, optimistic locking), then the question library (PR #3 — questions as owned, lifecycle-managed, taggable, reusable assets with their own API), then quiz composition (Phase 1.1 PR #1 — listing "my quizzes", searching the question library, and attaching/detaching/reordering questions on a quiz: the capabilities this RFC had reserved as "next steps" since PR #3, completed as a bridge so the frontend (RFC-009) could build the authoring workflow against real endpoints instead of stubs).

---

# Motivation

Quiz authoring is the heart of the product. Getting the aggregate boundaries right before exposing functionality prevents the most expensive mistake available here: welding questions to quizzes and losing the question bank (PRD roadmap v1.1), import/export, and reuse.

Internationalization is foundational for the same reason: BELC's congregation spans English, Kannada, Hindi, Tamil, Telugu, and Malayalam speakers. Retrofitting translations after text is welded into aggregate columns would be a schema-and-domain rewrite; introducing it before any API exists costs one migration.

---

# Goals

- A rich, framework-independent quiz domain that enforces its own invariants.
- Questions reusable across quizzes from day one — authored once, owned, and versionable into a question bank later.
- A typed question model that can grow beyond Bible quizzes without redesign.
- Multilingual content as a first-class capability: every participant can experience a quiz in their preferred language, while business logic never touches translated text.
- A tag vocabulary that can grow (synonyms, hierarchies, organizations) without touching questions.

---

# Non Goals

- Media upload (RFC-007), gameplay, sessions, scoring (RFC-004/006).
- Bible API integration; references are plain value objects.
- Question versioning/revisions — deferred until editing published content demands it.
- Translation APIs, automatic translation, AI generation, and import/export (the `source` enum reserves the provenance, nothing more).
- Sharing questions between authors or organizations — question bank, PRD v1.1.
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

`ownerIdentity` (embedded `IdentityReference`), `state`, `source`, `defaultLanguage`, localized content (title and prompt required, optional explanation — shown after answering, PRD), `questionType`, `difficulty` (EASY/MEDIUM/HARD, reserved as a future scoring multiplier), and five embedded collections: options, option localizations, Bible references, media references, tag ids.

**QuestionType is modeled from day one** so the platform can grow beyond BELC without redesigning the aggregate. The type carries the structural rules, revalidated on every option change:

- `SINGLE_CHOICE` — exactly one correct option.
- `MULTIPLE_CHOICE` — one or more correct options.
- `TRUE_FALSE` — exactly two options, exactly one correct.

Plus: at least one option, unique option ids and display orders, and complete option localization per stored language. Structural violations are `IllegalArgumentException` (invalid construction); the API boundary maps them to 400.

### Question lifecycle (PR #3)

`DRAFT → PUBLISHED → ARCHIVED`, deliberately mirroring the quiz lifecycle so authors learn one model:

- **DRAFT** — fully editable: difficulty, options, every translation, references, tags.
- **PUBLISHED** — **immutable** (`question.content.locked`, 409), and reusable by quizzes. A question is a shared asset the moment other people's quizzes can depend on it; silently changing what a quiz asks — or which answer is correct — after the fact is the failure mode this rule exists to prevent.
- **ARCHIVED** — terminal and read-only (`question.archived`, 409); unavailable for new quizzes while existing published quizzes keep functioning. Retained, never deleted (same reasoning as quizzes: played sessions must stay reconstructable).
- Drafts cannot be archived (`question.not-archivable`, 409) — a draft is edited or abandoned; archiving retires live content.

**Immutability includes translations, and that has a real cost**: today, adding a Kannada translation to an already-published question is impossible. That is the honest consequence of "published means frozen", but the workflow it blocks is legitimate — a church publishes in English, then translates a month later. Resolving it properly needs the revision concept (`QuizRevision` / question revisions) so a re-publish produces a new, identifiable version rather than mutating history under running quizzes. Deliberately deferred, not overlooked; see Open Questions.

**Ownership.** Questions carry `ownerIdentity` — always the authoring caller, never client-supplied. For now they are private assets: only the owner may read or modify them (others get 404, never a hint of existence). Cross-author reuse is real and wanted, but it needs a sharing model (visibility, organizations, or a shared bank) rather than an implicit "any author sees everything"; that arrives with the question bank (PRD v1.1). **Reusability across quizzes is unaffected** — that is a property of the aggregate boundary (quizzes hold question ids), not of who may read the question.

**Source.** `MANUAL | AI | IMPORT` — metadata only, no behavior varies by it. The authoring API always creates MANUAL; AI and IMPORT exist so the generation and import features can record provenance without a migration.

**Tags.** Questions hold `Set<UUID>` of tag ids — never strings. Tag is its own aggregate (id + normalized name, unique), so synonyms, descriptions, parent/child hierarchies, usage counts, and organization-specific vocabularies can grow on the tag without touching the Question model or migrating question rows. The API accepts and returns names; `TagResolver` resolves-or-creates by normalized name (`"Moses"`, `"  moses "` → one tag), with the unique index as the authority under concurrency (`tag.concurrent-creation`, 409 — retryable). Tags are language independent by design: a tag is a concept, not a word, so localized tag *labels* would live on the Tag aggregate later, never on the question. No search endpoint yet — tags are recorded now so the bank has something to index later.

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

`QuizCreatedEvent` (quizId, owner, occurredAt), `QuestionAddedToQuizEvent` (quizId, questionId, occurredAt), `QuizPublishedEvent` (quizId, occurredAt), `QuizArchivedEvent` (quizId, occurredAt), `QuestionPublishedEvent` (questionId, occurredAt), `QuestionArchivedEvent` (questionId, occurredAt). All are published by the application services per ADR-005 (`QuestionAddedToQuizEvent` since Phase 1.1 PR #1's attach API — reserved, unpublished, since PR #3). No consumers yet.

There is deliberately no `QuestionCreatedEvent`: nothing can react to a private draft, and an event without a plausible subscriber is dead code (the same rule that kept placeholder services out of PR #1). Publication and archival are the moments other parts of the system will care about.

Events reference aggregate ids only and are language independent — no localization events exist, and none are needed yet.

## Persistence

`V3__quiz_domain.sql` (additive, idempotent): `quizzes` (with flattened owner reference and settings columns), `questions`, `quiz_questions` (PK quiz+question, unique quiz+order, FKs both ways), `question_options` / `question_media_references` (unique display order per question), `question_bible_references`. CHECK constraints mirror every enum and range invariant.

`V4__content_i18n.sql` (content move, no data loss): adds `default_language` to `quizzes` and `questions`; creates `quiz_localizations` (PK quiz+language), `question_localizations` (PK question+language), and `option_localizations` (PK question+option+language), all `ON DELETE CASCADE` from their parent; copies every existing title, description, prompt, explanation, and option text into the default-language localization; then drops the old text columns. Aggregate identities are preserved — the migration is proven by an integration test that seeds a V3 database and migrates it. `option_localizations` has no composite FK to `question_options` deliberately: Hibernate rewrites both element collections independently, so option membership is enforced by the Question aggregate.

`V5__optimistic_locking.sql` adds a `version BIGINT` column to every aggregate table (identities, credentials, user_profiles, identity_sessions, quizzes, questions), backing JPA `@Version` on `AuditableEntity`.

`V6__question_library.sql` turns questions into owned, lifecycle-managed assets: adds `owner_identity_id` / `owner_identity_type`, `state`, `source` to `questions`; creates `tags` (unique name) and `question_tags` (PK question+tag). Two backfills make existing data legal, both integration-tested against a seeded V5 database:

- **Ownership** is inherited from the oldest quiz referencing each question — the closest thing to authorship that exists in pre-library data. A question no quiz references cannot be attributed, and the migration **stops with an actionable message** rather than inventing an owner; the operator attaches or removes it and re-runs.
- **State**: questions already used by a published or archived quiz become `PUBLISHED` — they are live content and must not be silently editable. Everything else becomes `DRAFT`.

Repositories: `QuizRepository`, `QuestionRepository`, `TagRepository` — plain, query methods arrive with the use cases that need them.

## Application layer

One application service per operation, per ADR-005 the only place aggregates are mutated, transactions opened, and events published:

```text
CreateQuizApplicationService.create(CurrentUser, CreateQuizCommand)            → authorize(QUIZ_CREATE)             → QuizCreatedEvent
UpdateQuizApplicationService.update(CurrentUser, UpdateQuizCommand)            → authorize(QUIZ_EDIT)  + ownership  → (no event)
PublishQuizApplicationService.publish(CurrentUser, PublishQuizCommand)         → authorize(QUIZ_EDIT)  + ownership  → QuizPublishedEvent
ArchiveQuizApplicationService.archive(CurrentUser, ArchiveQuizCommand)         → authorize(QUIZ_EDIT)  + ownership  → QuizArchivedEvent
QuizQueryService.quiz(CurrentUser, quizId)                                     → authorize(QUIZ_VIEW)  + visibility → (read only)

CreateQuestionApplicationService.create(CurrentUser, CreateQuestionCommand)    → authorize(QUIZ_CREATE)             → (no event)
UpdateQuestionApplicationService.update(CurrentUser, UpdateQuestionCommand)    → authorize(QUIZ_EDIT)  + ownership  → (no event)
PublishQuestionApplicationService.publish(CurrentUser, PublishQuestionCommand) → authorize(QUIZ_EDIT)  + ownership  → QuestionPublishedEvent
ArchiveQuestionApplicationService.archive(CurrentUser, ArchiveQuestionCommand) → authorize(QUIZ_EDIT)  + ownership  → QuestionArchivedEvent
QuestionQueryService.question(CurrentUser, questionId)                         → authorize(QUIZ_VIEW)  + ownership  → (read only)
QuestionQueryService.library(CurrentUser, QuestionSearchQuery, Pageable)       → authorize(QUIZ_VIEW)  + ownership  → (read only, paged)

AddQuestionToQuizApplicationService.add(CurrentUser, AddQuestionToQuizCommand)         → authorize(QUIZ_EDIT) + ownership → QuestionAddedToQuizEvent
RemoveQuestionFromQuizApplicationService.remove(CurrentUser, RemoveQuestionFromQuizCommand) → authorize(QUIZ_EDIT) + ownership → (no event)
ReorderQuizQuestionsApplicationService.reorder(CurrentUser, ReorderQuizQuestionsCommand)    → authorize(QUIZ_EDIT) + ownership → (no event)
QuizQueryService.mine(CurrentUser, QuizSearchQuery, Pageable)                          → authorize(QUIZ_VIEW)                → (read only, paged)
```

Questions reuse the quiz permissions (`QUIZ_CREATE`/`QUIZ_EDIT`/`QUIZ_VIEW`) rather than introducing `QUESTION_*`: authoring questions *is* quiz authoring — the permission model tracks what a person does, not which table it lands in, and a role that may create quizzes but not their questions has no product meaning. Dedicated permissions can split out if the question bank ever gives questions an independent audience.

Controllers resolve `CurrentUser` from `CurrentUserProvider` and delegate — zero authorization logic at the transport edge.

## Quiz composition: listing, search, attach, detach, reorder (Phase 1.1 PR #1)

The capabilities this RFC had reserved as "next steps" since the question library shipped — completed as a bridge PR once the frontend (RFC-009) needed real endpoints to build the authoring workflow against, rather than stubs.

**Attach — draft and published questions are both attachable.** `AddQuestionToQuizApplicationService` loads the quiz and question, enforces ownership of *both* (`QuestionOwnership.requireOwner` — a private question, even the caller's own, cannot be attached by anyone else, and there is no cross-author attachment), and rejects only an **archived** question (`quiz.question.not-attachable`, 409) — closing this RFC's own flagged gap ("ARCHIVED is not yet enforced at attachment"). A still-draft question is deliberately attachable: an author composes a quiz while its questions are still being refined, the same way the quiz itself stays DRAFT while assembled. Nothing here weakens the existing publish-time check (`PublishQuizApplicationService`) that every attached question must carry the quiz's default language by the time the *quiz* publishes — that remains the actual gate on quiz-readiness, not the question's own lifecycle state. The Quiz aggregate's `addQuestion` already allowed this (DRAFT or PUBLISHED quiz, never lose a question once published); the only change was exposing it over HTTP and adding the cross-aggregate archived-question check the aggregate cannot see on its own. Publishes `QuestionAddedToQuizEvent` (already defined, unused since PR #3).

**Detach and reorder are draft-quiz operations**, matching every other authored-content change (`localize`, `updateSettings`) rather than the one deliberate exception `addQuestion` makes for published quizzes. `Quiz.removeQuestion` already enforced this (`quiz.questions.locked`, 409, on a published quiz); the new `Quiz.reorder(List<UUID>)` domain method mirrors it exactly — same guard, same exception — and additionally requires the given list to name *exactly* the quiz's current questions, each once (`IllegalArgumentException` → 400 otherwise: missing, extra, or duplicated ids). Reordering reassigns `QuizQuestion.displayOrder` to `1..n` in the given order; ordering was already explicit and stored (never insertion-order-dependent), so no new persistence concept was needed — `QuizQuestion` stays the embeddable element inside the Quiz aggregate this RFC's "Alternatives Considered" already settled (an independent, repositoried entity was rejected: ordering invariants belong to the root). No domain event for either — pure composition bookkeeping has no subscriber, the same reasoning that keeps `QuestionCreatedEvent` from existing.

**Listing ("My Quizzes") and question library search** are read-only, owner-scoped, paginated (`Page<T>`, Spring Data), and filtered via `Specification` composition (`QuizSpecifications`, `QuestionSpecifications` — infrastructure, since they build JPA Criteria predicates): `QuizQueryService.mine` filters by state and title (matched against *any* localization, not only the default language); `QuestionQueryService.library` filters by state, difficulty, language, and tags (a question matches if it has *any* of the given tags — the conventional tag-filter OR-within-facet semantics), plus free-text search against any localization's title or prompt. There is deliberately **no cross-author listing or search** — every specification includes an owner predicate, so the restriction is structural, not a post-filter a bug could bypass. **Sorting is restricted to root-entity columns** (`updatedAt`, `createdAt`, `state`) and rejected otherwise (400) — a quiz or question's title lives in a per-language child collection, not a column, so "sort by title" has no single answer (which language?) and is refused rather than silently ignored. `@BatchSize(50)` on the localization, composition, and tag-id element collections turns the classic N+1 (one extra query per listed row to render its title/count/tags) into a handful of batched `IN` queries per page.

Both list endpoints return a concrete, hand-shaped page DTO (`QuizPageResponse` / `QuestionPageResponse`: `items`, `page`, `size`, `totalElements`, `totalPages`) rather than Spring's own `Page` JSON, which leaks framework internals into the public contract — the same discipline that keeps every other response a purpose-built DTO, never a serialized entity or framework type. The list items are lean summaries (`QuizSummaryView`/`QuestionSummaryView` → `QuizSummaryResponse`/`QuestionSummaryResponse`), not the full editable representation: a list of many quizzes should not carry settings or the full localization set, and a library page should not carry every language's option texts.

**Publication preconditions.** The aggregate enforces its own rules (DRAFT only, at least one question, default localization by construction). The service adds the single check that crosses aggregate boundaries: every attached question must be localized in the quiz's default language (`quiz.not-publishable`, 409, naming the offending question ids) — otherwise participants falling back to the default would face untranslated questions.

**Ownership model.** The owner is always `CurrentUser.reference()` — never accepted from the client. Mutations are owner-only; a non-owner gets 404 for a private quiz (existence is not disclosed) and 403 (`quiz.ownership.required`) for one it can see. Reads follow visibility: owners always, everyone else only for non-PRIVATE quizzes (UNLISTED is reachable by id; PUBLIC additionally discoverable once listing exists). Admin moderation of foreign quizzes will be a dedicated permission later, not an ownership bypass — nobody inspects roles.

**Optimistic locking.** Every aggregate carries a `@Version` (base entity). Read responses include the version; `PUT` requires it back and a stale value is rejected with 409 `quiz.concurrent-modification` before anything is applied — two authors editing the same draft can never silently overwrite each other. Same-instant races that slip past the check are caught by the JPA version at flush and surface as 409 `conflict.concurrent-modification`.

**Update semantics.** `PUT /quizzes/{id}` treats omitted fields as unchanged; a provided localization list replaces the full set of translations (the aggregate refuses to drop the default language). `PUT /questions/{id}` is a true PUT — every field is the complete new value, the localization list must include the default language, and options keep their ids so translations survive (new options carry fresh ids). Domain `IllegalArgumentException`s surface as 400 (`request.invalid`) via the global handler.

## Public API

```text
POST   /api/v1/quizzes                        201  create a draft (owner = caller)
GET    /api/v1/quizzes/mine                   200  the caller's own quizzes, filtered (state, search) and paged — Phase 1.1 PR #1
GET    /api/v1/quizzes/{quizId}               200  metadata, settings, state, visibility, default language, localizations, ordered question ids, version
PUT    /api/v1/quizzes/{quizId}               200  update visibility / settings / localizations (state-dependent)
POST   /api/v1/quizzes/{quizId}/publish       200  DRAFT → PUBLISHED
POST   /api/v1/quizzes/{quizId}/archive       200  PUBLISHED → ARCHIVED
POST   /api/v1/quizzes/{quizId}/questions              200  attach a question (draft or published; not archived) — Phase 1.1 PR #1
DELETE /api/v1/quizzes/{quizId}/questions/{questionId} 200  detach a question (draft quiz only) — Phase 1.1 PR #1
PATCH  /api/v1/quizzes/{quizId}/questions/order        200  reorder (draft quiz only; exactly the current set) — Phase 1.1 PR #1

POST   /api/v1/questions                      201  create a draft in its default language (owner = caller; option ids assigned by the server)
GET    /api/v1/questions                      200  the caller's own library, filtered (state, difficulty, language, tags, search) and paged — Phase 1.1 PR #1
GET    /api/v1/questions/{questionId}         200  metadata, options, every localization with option texts, references, tags, version
PUT    /api/v1/questions/{questionId}         200  replace the full editable representation (DRAFT only)
POST   /api/v1/questions/{questionId}/publish 200  DRAFT → PUBLISHED
POST   /api/v1/questions/{questionId}/archive 200  PUBLISHED → ARCHIVED
```

`GET /api/v1/quizzes/mine` deliberately does **not** live at the bare collection path (`GET /api/v1/quizzes`) — there is no "list all quizzes" endpoint, and there won't be one until the question-bank-era product decision about cross-author visibility (PRD v1.1) is made deliberately, not defaulted into. `GET /api/v1/questions`, by contrast, *is* the bare collection path — there was no pre-existing GET-by-collection route to collide with, and every result is still owner-scoped by the query itself, never a true "list all" the way `/quizzes/mine` was deliberately named to rule out.

Neither resource has a `DELETE`, deliberately. Published quizzes may have been played and published questions may sit inside other people's quizzes — sessions, scores, and history must stay reconstructable, so content is retired by archiving, never destroyed. A hard delete would also cascade into referencing data (the `quiz_questions` foreign keys) for no product benefit; drafts someone abandons simply stay drafts. If a true deletion need ever appears (privacy requests), it will be designed deliberately, not shipped as a default endpoint.

The question read model returns **no quiz references**: questions do not know where they are used. That direction of the relationship belongs to the quiz (which holds question ids), and inverting it would couple every question read to the quiz composition and leak other authors' quizzes into a question response. "Which quizzes use this question?" is a real future query — it belongs to the quiz side (a filter on quizzes), not to the question aggregate.

The quiz read API mirrors this: question ids only — embedding questions would couple the read models and bloat every quiz response.

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

**Tags as plain strings on the question** — rejected (review decision): a string tag can never grow. Synonyms, descriptions, parent/child hierarchies, usage counts, popular tags, and organization-specific vocabularies all become schema changes to every question row, and renaming a tag becomes a mass update with no identity to anchor it. `Tag` as an aggregate plus `Set<TagId>` on the question costs one table and a resolver now, and buys all of that for free later — the right trade for a platform meant to outgrow one church.

**Mutable published questions (edit in place)** — rejected: a published question is a shared asset other people's quizzes depend on; changing its wording or its correct answer under them is exactly the surprise the lifecycle exists to prevent. The cost — no translations after publish — is real and resolved by revisions, not by weakening immutability.

**`QUESTION_*` permissions** — rejected: authoring questions is quiz authoring. A role that may create quizzes but not their questions has no product meaning, and the permission model tracks what a person does, not which table it lands in.

**Attach requires the question to be PUBLISHED** — rejected (Phase 1.1 PR #1, caught before shipping): would have blocked the exact workflow an existing integration test already exercised — attach a still-draft question, keep refining its translations, publish the quiz once ready. Only ARCHIVED is rejected; the quiz's own publish-time check is what actually gates readiness.

**Hand-rolled JPQL with null-coalescing optional parameters for filtered search** — rejected: JPA/Hibernate has no portable way to say "this list parameter is absent" inside a static query (`:param IS NULL` on a collection-valued parameter is provider-fragile), and combining several optional to-many joins in one JPQL string gets unreadable fast. `Specification` composition (`.and(null)` is a documented no-op) gives the same result — one predicate per filter, skipped when absent — without the string-templating.

**Exposing Spring Data's `Page<T>` JSON directly** — rejected: it serializes framework internals (`pageable`, `sort` objects, `empty`, etc.) into the public contract, coupling the API surface to a library upgrade. A concrete `QuizPageResponse`/`QuestionPageResponse` (`items`, `page`, `size`, `totalElements`, `totalPages`) is one more DTO but a clean, versioned OpenAPI schema — the same trade every other endpoint already makes.

**Sorting by content (e.g., title)** — rejected: title lives in a per-language localization row, not a root column: "sort by title" has no single answer when a quiz has three translations. Sortable properties are allow-listed to root columns (`updatedAt`, `createdAt`, `state`) and anything else is a 400, not a silent no-op.

---

# Risks

- Element collections rewrite on change (delete + insert); fine at authoring scale, revisit only if question editing becomes hot.
- `replaceOptions` silently drops translations left incomplete by the change. Authoring UX should surface this (a future response could name the invalidated languages); the domain rule itself is correct.
- **Publishing is one-way and freezes translations.** Until revisions exist, an author who publishes before translating must create a new question. Acceptable while BELC authors in one language; the first multilingual publishing cycle will make this urgent.
- Tag creation races surface as a retryable 409 rather than resolving silently; if authoring UX ever creates many tags at once, resolve-or-retry belongs in the resolver.
- **List/search filtering loads full aggregates then applies `Specification` predicates via JPA Criteria** (not a lighter projection) — fine at authoring scale (one owner's quizzes/questions, a few hundred at most; `@BatchSize` keeps it to a handful of queries per page), revisit only if it becomes a measured hot path.

---

# Migration

`V3__quiz_domain.sql` is additive; existing data is unaffected. `V4__content_i18n.sql` moves content into localization tables with no data loss (integration-tested against a seeded V3 database); existing quizzes and questions keep their identities and become default-language (`en`) localized. `V5__optimistic_locking.sql` is additive; existing rows start at version 0. `V6__question_library.sql` backfills question ownership from the referencing quiz and promotes questions inside published quizzes to `PUBLISHED` (integration-tested against a seeded V5 database); it refuses to run rather than invent an owner for a question no quiz references.

---

# Open Questions

- **Revisions.** Publishing is immutable, so translating or correcting published content requires a new question today. A revision counter (incremented per publish, recorded by sessions) would let content evolve while keeping played history identifiable — the first real need for it will come from either multilingual publishing or a typo in a live quiz.
- **Sharing questions across authors.** Questions are private assets; reuse across quizzes works, reuse across *people* does not. Needs a deliberate model (visibility, organizations, or a shared bank), not an implicit widening — question bank, PRD v1.1.
- Localized display names for Bible books (rendering concern) — deferred until a UI needs them.

*Resolved since PR #1:* quiz deletion (there is none — archive instead), question deletion (likewise), question ownership (the authoring identity, owner-only access).

*Resolved since PR #3 (Phase 1.1 PR #1):* whether archived questions should disappear from drafts that already reference them — **no**: archival only blocks *new* attachment (`quiz.question.not-attachable`), matching "existing published quizzes keep functioning" exactly; a draft that already referenced a question before it was archived keeps that reference, since a draft's questions were never a promise to anyone but its own author, and stripping them silently on someone else's archival action would be a surprising, unrequested mutation. Whether draft questions should be attachable at all was also resolved — **yes**: an author composes a quiz while its questions are still being refined, so only ARCHIVED is rejected, not DRAFT.

---

# Acceptance Criteria

- [x] Quiz, Question, QuizQuestion modeled; questions reusable across quizzes (integration-tested).
- [x] All invariants enforced by aggregates with unit coverage, including the per-type option rules.
- [x] Content is multilingual: `LanguageCode` value object, per-language localizations for quiz, question, and option text; structure and gameplay stay language independent.
- [x] Localization invariants enforced by the roots: one per language, default language always present, option coverage complete per language.
- [x] Domain events defined; language independent.
- [x] `V3__quiz_domain.sql`, `V4__content_i18n.sql`, `V5__optimistic_locking.sql`, and `V6__question_library.sql` apply incrementally on an existing database with no data loss; Hibernate validates the mapping (Testcontainers).
- [x] Quiz domain is framework-independent (ArchUnit).
- [x] Quiz authoring API: create/read/update/publish/archive with the draft-first lifecycle, owner-only mutations from `CurrentUser`, `AuthorizationService` for every operation, and OpenAPI documentation (integration-tested end to end).
- [x] Optimistic locking on every aggregate; stale saves receive 409 instead of overwriting.
- [x] Question library: questions are owned, lifecycle-managed (draft → published → archived), multilingual, taggable, reusable assets with their own API; publication freezes them and archival retires them without breaking existing quizzes.
- [x] Tags are their own aggregate; questions reference tag ids, so tag vocabulary can grow without touching questions.
- [x] Quiz composition over HTTP (Phase 1.1 PR #1): attach (draft or published question, not archived; `QuestionAddedToQuizEvent`), detach and reorder (draft quiz only; exactly the current question set), all owner-enforced and atomic (integration-tested end to end, including the full create → list → attach → reorder → remove → publish workflow).
- [x] "My Quizzes" and question library listing/search: owner-scoped, filtered (state/difficulty/language/tags/title/text), paginated, sortable on root columns only, `@BatchSize`-protected against N+1, documented in OpenAPI.

---

# Future Work

- Question and quiz revisions — the prerequisite for translating or correcting published content (see Open Questions).
- Participant language preference at session join (RFC-004) — the consumer of this model.
- Question bank: sharing questions across authors and organizations, browsing, import/export (v1.1), including translations.
- Tag vocabulary growth (synonyms, descriptions, hierarchies, usage counts) — the reason Tag is an aggregate.
- Localized Bible book display names, translation tooling, and Bible API integration — when demanded.
