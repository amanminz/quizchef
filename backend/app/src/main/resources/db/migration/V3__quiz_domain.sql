-- Quiz bounded context: quizzes (metadata, settings, lifecycle), reusable
-- questions with embedded value objects, and the quiz_questions composition.
-- Quizzes reference questions by id only — questions are their own aggregate
-- and are reusable across quizzes. No gameplay tables.

CREATE TABLE IF NOT EXISTS quizchef.quizzes (
    id                              UUID          PRIMARY KEY,
    title                           VARCHAR(200)  NOT NULL,
    description                     VARCHAR(2000),
    owner_identity_id               UUID          NOT NULL,
    owner_identity_type             VARCHAR(20)   NOT NULL CHECK (owner_identity_type IN ('REGISTERED', 'GUEST')),
    visibility                      VARCHAR(20)   NOT NULL CHECK (visibility IN ('PRIVATE', 'UNLISTED', 'PUBLIC')),
    state                           VARCHAR(20)   NOT NULL CHECK (state IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    randomize_question_order        BOOLEAN       NOT NULL,
    randomize_option_order          BOOLEAN       NOT NULL,
    question_time_limit_seconds     INTEGER       NOT NULL CHECK (question_time_limit_seconds BETWEEN 5 AND 300),
    show_leaderboard_after_question BOOLEAN       NOT NULL,
    show_explanation_after_question BOOLEAN       NOT NULL,
    created_at                      TIMESTAMPTZ   NOT NULL,
    updated_at                      TIMESTAMPTZ   NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_quizzes_owner_identity_id
    ON quizchef.quizzes (owner_identity_id);

CREATE TABLE IF NOT EXISTS quizchef.questions (
    id            UUID          PRIMARY KEY,
    title         VARCHAR(200)  NOT NULL,
    prompt        VARCHAR(4000) NOT NULL,
    explanation   VARCHAR(4000),
    question_type VARCHAR(30)   NOT NULL CHECK (question_type IN ('SINGLE_CHOICE', 'MULTIPLE_CHOICE', 'TRUE_FALSE')),
    difficulty    VARCHAR(20)   NOT NULL CHECK (difficulty IN ('EASY', 'MEDIUM', 'HARD')),
    created_at    TIMESTAMPTZ   NOT NULL,
    updated_at    TIMESTAMPTZ   NOT NULL
);

CREATE TABLE IF NOT EXISTS quizchef.quiz_questions (
    quiz_id       UUID    NOT NULL REFERENCES quizchef.quizzes (id) ON DELETE CASCADE,
    question_id   UUID    NOT NULL REFERENCES quizchef.questions (id),
    display_order INTEGER NOT NULL CHECK (display_order >= 1),
    PRIMARY KEY (quiz_id, question_id),
    UNIQUE (quiz_id, display_order)
);

CREATE TABLE IF NOT EXISTS quizchef.question_options (
    question_id   UUID         NOT NULL REFERENCES quizchef.questions (id) ON DELETE CASCADE,
    id            UUID         NOT NULL,
    text          VARCHAR(500) NOT NULL,
    correct       BOOLEAN      NOT NULL,
    display_order INTEGER      NOT NULL CHECK (display_order >= 1),
    PRIMARY KEY (question_id, id),
    UNIQUE (question_id, display_order)
);

CREATE TABLE IF NOT EXISTS quizchef.question_bible_references (
    question_id UUID        NOT NULL REFERENCES quizchef.questions (id) ON DELETE CASCADE,
    book        VARCHAR(50) NOT NULL,
    chapter     INTEGER     NOT NULL CHECK (chapter >= 1),
    verse_start INTEGER     NOT NULL CHECK (verse_start >= 1),
    verse_end   INTEGER     CHECK (verse_end >= verse_start),
    translation VARCHAR(20)
);

CREATE INDEX IF NOT EXISTS idx_question_bible_references_question_id
    ON quizchef.question_bible_references (question_id);

CREATE TABLE IF NOT EXISTS quizchef.question_media_references (
    question_id   UUID         NOT NULL REFERENCES quizchef.questions (id) ON DELETE CASCADE,
    id            UUID         NOT NULL,
    media_type    VARCHAR(20)  NOT NULL CHECK (media_type IN ('IMAGE', 'AUDIO', 'VIDEO')),
    storage_key   VARCHAR(512) NOT NULL,
    alt_text      VARCHAR(500),
    display_order INTEGER      NOT NULL CHECK (display_order >= 1),
    PRIMARY KEY (question_id, id),
    UNIQUE (question_id, display_order)
);
