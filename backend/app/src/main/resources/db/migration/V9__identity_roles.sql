-- Durable role storage (Phase 3 PR #1): roles move from an implicit,
-- login-time constant onto the Identity aggregate. Guests hold no roles;
-- every REGISTERED identity holds USER from birth, further roles
-- (QUIZ_MASTER via host onboarding, ADMIN) are granted additively.

CREATE TABLE IF NOT EXISTS quizchef.identity_roles (
    identity_id UUID        NOT NULL REFERENCES quizchef.identities (id),
    role        VARCHAR(20) NOT NULL CHECK (role IN ('ADMIN', 'QUIZ_MASTER', 'USER')),
    CONSTRAINT uq_identity_roles UNIQUE (identity_id, role)
);

CREATE INDEX IF NOT EXISTS idx_identity_roles_identity
    ON quizchef.identity_roles (identity_id);

-- Backfill: the implicit USER authority every registered identity has
-- carried since RFC-002 becomes a durable fact. Guests get nothing.
INSERT INTO quizchef.identity_roles (identity_id, role)
SELECT id, 'USER'
FROM quizchef.identities
WHERE identity_type = 'REGISTERED';
