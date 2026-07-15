-- Question library: questions become owned, lifecycle-managed, reusable
-- assets. Adds ownership, state (DRAFT/PUBLISHED/ARCHIVED), source
-- metadata, and tags (their own table — questions hold tag ids so
-- synonyms, hierarchies, and vocabularies can grow on the Tag without
-- touching questions).

ALTER TABLE quizchef.questions
    ADD COLUMN IF NOT EXISTS owner_identity_id   UUID,
    ADD COLUMN IF NOT EXISTS owner_identity_type VARCHAR(20) CHECK (owner_identity_type IN ('REGISTERED', 'GUEST')),
    ADD COLUMN IF NOT EXISTS state               VARCHAR(20) NOT NULL DEFAULT 'DRAFT' CHECK (state IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    ADD COLUMN IF NOT EXISTS source              VARCHAR(20) NOT NULL DEFAULT 'MANUAL' CHECK (source IN ('MANUAL', 'AI', 'IMPORT'));

-- Ownership backfill: the owner of the oldest quiz referencing the
-- question. Pre-v1 databases contain questions only inside quiz
-- compositions; a question that cannot be attributed fails the NOT NULL
-- promotion loudly rather than receiving a fabricated owner.
UPDATE quizchef.questions
SET owner_identity_id   = attributed.owner_identity_id,
    owner_identity_type = attributed.owner_identity_type
FROM (
    SELECT DISTINCT ON (quiz_questions.question_id)
           quiz_questions.question_id,
           quizzes.owner_identity_id,
           quizzes.owner_identity_type
    FROM quizchef.quiz_questions quiz_questions
    JOIN quizchef.quizzes quizzes ON quizzes.id = quiz_questions.quiz_id
    ORDER BY quiz_questions.question_id, quizzes.created_at
) attributed
WHERE quizchef.questions.id = attributed.question_id
  AND quizchef.questions.owner_identity_id IS NULL;

-- Questions already used by a published (or archived) quiz are live
-- content: they become PUBLISHED — and therefore immutable — rather than
-- editable drafts.
UPDATE quizchef.questions
SET state = 'PUBLISHED'
WHERE state = 'DRAFT'
  AND EXISTS (
    SELECT 1
    FROM quizchef.quiz_questions quiz_questions
    JOIN quizchef.quizzes quizzes ON quizzes.id = quiz_questions.quiz_id
    WHERE quiz_questions.question_id = quizchef.questions.id
      AND quizzes.state IN ('PUBLISHED', 'ARCHIVED'));

-- A question no quiz references cannot be attributed. Rather than invent an
-- owner, stop with an actionable message: the operator attaches it to a quiz
-- or removes it, then re-runs.
DO $$
DECLARE
    unattributed BIGINT;
BEGIN
    SELECT count(*) INTO unattributed
    FROM quizchef.questions
    WHERE owner_identity_id IS NULL;

    IF unattributed > 0 THEN
        RAISE EXCEPTION 'V6 cannot attribute % question(s) to an owner: no quiz references them. '
                        'Attach them to a quiz or remove them, then re-run this migration.', unattributed;
    END IF;
END $$;

ALTER TABLE quizchef.questions
    ALTER COLUMN owner_identity_id SET NOT NULL,
    ALTER COLUMN owner_identity_type SET NOT NULL,
    ALTER COLUMN state DROP DEFAULT,
    ALTER COLUMN source DROP DEFAULT;

CREATE INDEX IF NOT EXISTS idx_questions_owner_identity_id
    ON quizchef.questions (owner_identity_id);

CREATE TABLE IF NOT EXISTS quizchef.tags (
    id         UUID        PRIMARY KEY,
    name       VARCHAR(50) NOT NULL UNIQUE,
    version    BIGINT      NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS quizchef.question_tags (
    question_id UUID NOT NULL REFERENCES quizchef.questions (id) ON DELETE CASCADE,
    tag_id      UUID NOT NULL REFERENCES quizchef.tags (id),
    PRIMARY KEY (question_id, tag_id)
);
