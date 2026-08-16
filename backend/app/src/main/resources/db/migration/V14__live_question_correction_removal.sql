-- Correcting and removing questions during a live session.
--
-- A Quiz Master who discovers a bad question mid-event has to be able to fix
-- it or pull it without abandoning the game. Neither may touch the Question
-- Library: published questions are immutable and sessions pin the version
-- they execute, so editing the library row would silently change every other
-- quiz using it and rewrite what past sessions asked.
--
-- So both live here, scoped to one session, exactly as session_question_order
-- (V13) already scopes the *order* a session plays. A second session of the
-- same quiz still asks the question this one dropped, with the wording this
-- one corrected away.

-- Questions the host pulled out of this session.
--
-- A marker, not a deletion. Everything that reads the session skips these
-- (numbering, progression, scoring, history), so a player never sees
-- "Question 3 of 10" followed by "Question 5 of 10" -- but the fact that a
-- host removed it, from which phase, and how many accepted answers that
-- cancelled outlives the game as the session's own audit entry.
CREATE TABLE IF NOT EXISTS quizchef.session_removed_questions (
    session_id             UUID        NOT NULL REFERENCES quizchef.sessions (id) ON DELETE CASCADE,
    question_id            UUID        NOT NULL,
    removed_at             TIMESTAMPTZ NOT NULL,
    -- Null when the question had not been reached yet.
    removed_from_phase     VARCHAR(20),
    cancelled_answer_count INTEGER     NOT NULL DEFAULT 0,
    PRIMARY KEY (session_id, question_id)
);

-- One session's corrected copy of one question.
--
-- An overlay, not a replacement: a language the host did not correct keeps
-- its authored text, and an option they did not reword keeps its own. What a
-- correction may change is bounded to the prompt, the option wording, and
-- which options are correct -- never the option set itself, because already
-- recorded answers point at those ids and a changed set would leave the
-- cancelled attempt and the replayed one incomparable.
CREATE TABLE IF NOT EXISTS quizchef.session_question_corrections (
    id          UUID        NOT NULL PRIMARY KEY,
    session_id  UUID        NOT NULL REFERENCES quizchef.sessions (id) ON DELETE CASCADE,
    question_id UUID        NOT NULL,
    -- How many times the host has corrected this question in this session,
    -- starting at 1. Distinct from `version`, which is the optimistic lock.
    revision    INTEGER     NOT NULL DEFAULT 1,
    version     BIGINT      NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT session_question_corrections_unique_question UNIQUE (session_id, question_id)
);

CREATE INDEX IF NOT EXISTS idx_session_question_corrections_session
    ON quizchef.session_question_corrections (session_id);

-- Which options the corrected question treats as correct. Replaces the
-- authored answer key outright -- fixing a wrong key is the single most
-- common reason to pull a question.
CREATE TABLE IF NOT EXISTS quizchef.session_question_correction_correct_options (
    correction_id UUID NOT NULL
        REFERENCES quizchef.session_question_corrections (id) ON DELETE CASCADE,
    option_id     UUID NOT NULL,
    PRIMARY KEY (correction_id, option_id)
);

-- The corrected prompt, per language.
CREATE TABLE IF NOT EXISTS quizchef.session_question_correction_prompts (
    correction_id UUID          NOT NULL
        REFERENCES quizchef.session_question_corrections (id) ON DELETE CASCADE,
    language_code VARCHAR(20)   NOT NULL,
    prompt        VARCHAR(2000) NOT NULL,
    PRIMARY KEY (correction_id, language_code)
);

-- One option's corrected wording, per language.
CREATE TABLE IF NOT EXISTS quizchef.session_question_correction_option_texts (
    correction_id UUID          NOT NULL
        REFERENCES quizchef.session_question_corrections (id) ON DELETE CASCADE,
    language_code VARCHAR(20)   NOT NULL,
    option_id     UUID          NOT NULL,
    text          VARCHAR(1000) NOT NULL,
    PRIMARY KEY (correction_id, language_code, option_id)
);
