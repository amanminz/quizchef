-- Session bounded context: a live run of a published quiz. Sessions own
-- their roster (ordering + membership + within-session key uniqueness);
-- participants are durable (ADR-003) and own their own answers and score.
-- No transport state anywhere (ADR-004). No gameplay tables.

CREATE TABLE IF NOT EXISTS quizchef.sessions (
    id                             UUID        PRIMARY KEY,
    session_pin                    VARCHAR(6)  NOT NULL CHECK (session_pin ~ '^[0-9]{6}$'),
    published_quiz_version_id      UUID        NOT NULL,
    host_identity_id               UUID        NOT NULL,
    host_identity_type             VARCHAR(20) NOT NULL CHECK (host_identity_type IN ('REGISTERED', 'GUEST')),
    state                          VARCHAR(20) NOT NULL CHECK (state IN ('CREATED', 'LOBBY', 'IN_PROGRESS', 'FINISHED', 'ARCHIVED')),
    current_question_id            UUID,
    current_phase                  VARCHAR(20) CHECK (current_phase IN ('QUESTION', 'REVEAL', 'LEADERBOARD')),
    allow_late_join                BOOLEAN     NOT NULL,
    allow_reconnect                BOOLEAN     NOT NULL,
    show_live_leaderboard          BOOLEAN     NOT NULL,
    max_participants               INTEGER     NOT NULL CHECK (max_participants BETWEEN 1 AND 1000),
    current_timer_started_at       TIMESTAMPTZ,
    current_timer_duration_seconds INTEGER,
    current_timer_ends_at          TIMESTAMPTZ,
    version                        BIGINT      NOT NULL,
    created_at                     TIMESTAMPTZ NOT NULL,
    updated_at                     TIMESTAMPTZ NOT NULL
);

-- A pin is unique among active sessions and reusable once archived.
CREATE UNIQUE INDEX IF NOT EXISTS idx_sessions_active_pin
    ON quizchef.sessions (session_pin)
    WHERE state <> 'ARCHIVED';

CREATE INDEX IF NOT EXISTS idx_sessions_host_identity_id
    ON quizchef.sessions (host_identity_id);

-- The session's roster: an element collection inside the Session aggregate.
-- Carries the immutable ParticipantKey (identity XOR guest token) so the
-- aggregate enforces within-session uniqueness; the partial unique indexes
-- are the database backstop.
CREATE TABLE IF NOT EXISTS quizchef.session_participants (
    session_id     UUID        NOT NULL REFERENCES quizchef.sessions (id) ON DELETE CASCADE,
    participant_id UUID        NOT NULL,
    identity_id    UUID,
    identity_type  VARCHAR(20) CHECK (identity_type IN ('REGISTERED', 'GUEST')),
    guest_token    VARCHAR(512),
    join_order     INTEGER     NOT NULL CHECK (join_order >= 1),
    PRIMARY KEY (session_id, participant_id),
    UNIQUE (session_id, join_order),
    CONSTRAINT chk_roster_key_exactly_one
        CHECK ((identity_id IS NOT NULL) <> (guest_token IS NOT NULL))
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_session_participants_identity
    ON quizchef.session_participants (session_id, identity_id)
    WHERE identity_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS idx_session_participants_guest
    ON quizchef.session_participants (session_id, guest_token)
    WHERE guest_token IS NOT NULL;

-- Participants: durable, own their answers and cached score. Connectivity is
-- derived from state (no stored flag). A registered participant carries an
-- identity; a guest carries a globally unique reconnection token.
CREATE TABLE IF NOT EXISTS quizchef.participants (
    id                 UUID         PRIMARY KEY,
    session_id         UUID         NOT NULL REFERENCES quizchef.sessions (id) ON DELETE CASCADE,
    identity_id        UUID,
    identity_type      VARCHAR(20)  CHECK (identity_type IN ('REGISTERED', 'GUEST')),
    guest_token        VARCHAR(512) UNIQUE,
    display_name       VARCHAR(100) NOT NULL,
    preferred_language VARCHAR(20)  NOT NULL,
    last_seen_at       TIMESTAMPTZ,
    total_score        INTEGER      NOT NULL,
    state              VARCHAR(20)  NOT NULL CHECK (state IN ('JOINED', 'CONNECTED', 'DISCONNECTED', 'FINISHED')),
    version            BIGINT       NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL,
    updated_at         TIMESTAMPTZ  NOT NULL,
    CONSTRAINT chk_participant_identity_exactly_one
        CHECK ((identity_id IS NOT NULL) <> (guest_token IS NOT NULL))
);

CREATE INDEX IF NOT EXISTS idx_participants_session_id
    ON quizchef.participants (session_id);

-- Participant answers: owned by the participant, one per question.
CREATE TABLE IF NOT EXISTS quizchef.participant_answers (
    participant_id      UUID        NOT NULL REFERENCES quizchef.participants (id) ON DELETE CASCADE,
    question_id         UUID        NOT NULL,
    selected_option_ids TEXT        NOT NULL,
    answered_language   VARCHAR(20) NOT NULL,
    submitted_at        TIMESTAMPTZ NOT NULL,
    response_time_millis BIGINT     NOT NULL CHECK (response_time_millis >= 0),
    points_awarded      INTEGER     NOT NULL CHECK (points_awarded >= 0),
    PRIMARY KEY (participant_id, question_id)
);
