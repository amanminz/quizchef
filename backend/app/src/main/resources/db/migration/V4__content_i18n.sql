-- Content internationalization: displayable text moves out of the aggregate
-- tables into per-language localization tables so a quiz can be experienced
-- in any language. Structure stays language neutral — correctness, ordering,
-- types, and references never vary by translation.
--
-- Each quiz and question carries its own default language; the localization
-- for that language always exists (domain-enforced — not expressible as a
-- table constraint). Existing content becomes the 'en' localization, the
-- language everything so far was authored in. No rows are lost: titles,
-- prompts, and option texts are copied before the old columns are dropped.

-- Quiz content --------------------------------------------------------------

ALTER TABLE quizchef.quizzes
    ADD COLUMN IF NOT EXISTS default_language VARCHAR(20) NOT NULL DEFAULT 'en';

CREATE TABLE IF NOT EXISTS quizchef.quiz_localizations (
    quiz_id       UUID          NOT NULL REFERENCES quizchef.quizzes (id) ON DELETE CASCADE,
    language_code VARCHAR(20)   NOT NULL,
    title         VARCHAR(200)  NOT NULL,
    description   VARCHAR(2000),
    PRIMARY KEY (quiz_id, language_code)
);

INSERT INTO quizchef.quiz_localizations (quiz_id, language_code, title, description)
SELECT id, default_language, title, description
FROM quizchef.quizzes
ON CONFLICT DO NOTHING;

ALTER TABLE quizchef.quizzes
    DROP COLUMN IF EXISTS title,
    DROP COLUMN IF EXISTS description,
    ALTER COLUMN default_language DROP DEFAULT;

-- Question content ----------------------------------------------------------

ALTER TABLE quizchef.questions
    ADD COLUMN IF NOT EXISTS default_language VARCHAR(20) NOT NULL DEFAULT 'en';

CREATE TABLE IF NOT EXISTS quizchef.question_localizations (
    question_id   UUID          NOT NULL REFERENCES quizchef.questions (id) ON DELETE CASCADE,
    language_code VARCHAR(20)   NOT NULL,
    title         VARCHAR(200)  NOT NULL,
    prompt        VARCHAR(4000) NOT NULL,
    explanation   VARCHAR(4000),
    PRIMARY KEY (question_id, language_code)
);

INSERT INTO quizchef.question_localizations (question_id, language_code, title, prompt, explanation)
SELECT id, default_language, title, prompt, explanation
FROM quizchef.questions
ON CONFLICT DO NOTHING;

ALTER TABLE quizchef.questions
    DROP COLUMN IF EXISTS title,
    DROP COLUMN IF EXISTS prompt,
    DROP COLUMN IF EXISTS explanation,
    ALTER COLUMN default_language DROP DEFAULT;

-- Option content ------------------------------------------------------------
-- Correctness and display order stay on question_options; only the text is
-- per language. No composite FK to question_options: Hibernate rewrites both
-- element collections independently, so option membership is enforced by the
-- Question aggregate (and the PK blocks duplicates).

CREATE TABLE IF NOT EXISTS quizchef.option_localizations (
    question_id   UUID         NOT NULL REFERENCES quizchef.questions (id) ON DELETE CASCADE,
    option_id     UUID         NOT NULL,
    language_code VARCHAR(20)  NOT NULL,
    text          VARCHAR(500) NOT NULL,
    PRIMARY KEY (question_id, option_id, language_code)
);

INSERT INTO quizchef.option_localizations (question_id, option_id, language_code, text)
SELECT question_options.question_id, question_options.id, questions.default_language, question_options.text
FROM quizchef.question_options question_options
JOIN quizchef.questions questions ON questions.id = question_options.question_id
ON CONFLICT DO NOTHING;

ALTER TABLE quizchef.question_options
    DROP COLUMN IF EXISTS text;
