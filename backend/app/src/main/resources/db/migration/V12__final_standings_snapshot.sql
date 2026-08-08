-- The standings as they were when a session finished.
--
-- Deliberately a snapshot rather than a view over live data. The host's
-- results read projects standings from participants' current rows and ranks
-- them at read time (ADR-006), which is right for a running game and wrong
-- for history: a later change to the ranking rule would silently rewrite
-- what happened at an event that already happened, and a display name
-- edited afterwards would retroactively rename someone in a past result.
--
-- Written once, when the session finishes. Never updated.
CREATE TABLE final_standings (
    id                       UUID PRIMARY KEY,
    session_id               UUID        NOT NULL REFERENCES sessions (id) ON DELETE CASCADE,
    participant_id           UUID        NOT NULL,
    -- The name as it read at completion, not a reference to a mutable one.
    display_name_at_completion VARCHAR(100) NOT NULL,
    final_rank               INTEGER     NOT NULL,
    final_score              INTEGER     NOT NULL,
    captured_at              TIMESTAMPTZ NOT NULL,
    -- The audit columns AuditableEntity carries on every table.
    version                  BIGINT      NOT NULL,
    created_at               TIMESTAMPTZ NOT NULL,
    updated_at               TIMESTAMPTZ NOT NULL,
    CONSTRAINT final_standings_unique_participant UNIQUE (session_id, participant_id),
    CONSTRAINT final_standings_unique_rank UNIQUE (session_id, final_rank),
    CONSTRAINT final_standings_rank_positive CHECK (final_rank > 0),
    CONSTRAINT final_standings_score_not_negative CHECK (final_score >= 0)
);

-- Every read is "the standings for this session, in order".
CREATE INDEX idx_final_standings_session_rank ON final_standings (session_id, final_rank);
