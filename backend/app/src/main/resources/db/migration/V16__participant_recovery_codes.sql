-- Host-assisted recovery for a player whose browser lost its credential.
--
-- The resume token is the only thing that proves an anonymous player is who
-- they say, so a player whose storage is gone can prove nothing at all --
-- and must not be let in on the strength of their name, which anyone in the
-- room can hear. The authority has to come from somewhere else: the host,
-- physically looking at the person.
--
-- Six digits is a small space, and the digest is not what protects it. A
-- code lives about five minutes, works exactly once, names one participant
-- in one session, and its redemption endpoint is rate limited. The digest
-- exists so a leaked database does not hand over codes that are still live.

CREATE TABLE IF NOT EXISTS quizchef.participant_recovery_codes (
    id             UUID        NOT NULL PRIMARY KEY,
    session_id     UUID        NOT NULL REFERENCES quizchef.sessions (id) ON DELETE CASCADE,
    participant_id UUID        NOT NULL REFERENCES quizchef.participants (id) ON DELETE CASCADE,
    code_digest    VARCHAR(64) NOT NULL,
    expires_at     TIMESTAMPTZ NOT NULL,
    -- Set the instant it is used; a code is never usable twice.
    redeemed_at    TIMESTAMPTZ,
    -- The audit columns AuditableEntity carries on every table.
    version        BIGINT      NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL,
    updated_at     TIMESTAMPTZ NOT NULL
);

-- Redemption knows the session (from the PIN) and the digits, nothing else.
CREATE INDEX IF NOT EXISTS idx_participant_recovery_codes_lookup
    ON quizchef.participant_recovery_codes (session_id, code_digest);

-- Issuing a new code supersedes any outstanding one for that participant.
CREATE INDEX IF NOT EXISTS idx_participant_recovery_codes_participant
    ON quizchef.participant_recovery_codes (participant_id);

-- Recovery rotates the participant's resume token, so the digest that was
-- write-once at join is now updatable. Both copies move together: the
-- participant's own, and the session roster's mirror of it (ParticipantKey),
-- which enforces "one guest token per session".
COMMENT ON COLUMN quizchef.participants.guest_token
    IS 'SHA-256 of the resume token. Rotated by host-assisted recovery; mirrored in session_participants.guest_token.';
