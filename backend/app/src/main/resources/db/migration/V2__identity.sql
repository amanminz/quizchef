-- Identity bounded context: durable actors, credentials, profiles, and
-- login sessions. One table per aggregate; aggregates reference the identity
-- by id only — no cross-aggregate object relationships.

CREATE TABLE IF NOT EXISTS quizchef.identities (
    id            UUID        PRIMARY KEY,
    identity_type VARCHAR(20) NOT NULL CHECK (identity_type IN ('REGISTERED', 'GUEST')),
    status        VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'DISABLED')),
    created_at    TIMESTAMPTZ NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS quizchef.credentials (
    id            UUID         PRIMARY KEY,
    identity_id   UUID         NOT NULL UNIQUE REFERENCES quizchef.identities (id),
    password_hash VARCHAR(255) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL
);

CREATE TABLE IF NOT EXISTS quizchef.user_profiles (
    id           UUID          PRIMARY KEY,
    identity_id  UUID          NOT NULL UNIQUE REFERENCES quizchef.identities (id),
    display_name VARCHAR(50)   NOT NULL,
    email        VARCHAR(320)  NOT NULL UNIQUE,
    phone_number VARCHAR(32),
    avatar_url   VARCHAR(2048),
    created_at   TIMESTAMPTZ   NOT NULL,
    updated_at   TIMESTAMPTZ   NOT NULL
);

CREATE TABLE IF NOT EXISTS quizchef.identity_sessions (
    id                 UUID         PRIMARY KEY,
    identity_id        UUID         NOT NULL REFERENCES quizchef.identities (id),
    refresh_token_hash VARCHAR(255),
    device_fingerprint VARCHAR(255),
    user_agent         VARCHAR(512),
    ip_address         VARCHAR(45),
    last_seen_at           TIMESTAMPTZ  NOT NULL,
    last_authenticated_at  TIMESTAMPTZ  NOT NULL,
    revoked                BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at         TIMESTAMPTZ  NOT NULL,
    updated_at         TIMESTAMPTZ  NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_identity_sessions_identity_id
    ON quizchef.identity_sessions (identity_id);
