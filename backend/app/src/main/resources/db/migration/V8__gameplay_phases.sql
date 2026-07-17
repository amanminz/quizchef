-- The execution engine drives the session through real gameplay phases.
-- V7 modeled a placeholder set (QUESTION / REVEAL / LEADERBOARD); replace the
-- check with the phases the engine actually transitions through. No data
-- migration: no session has been played yet, so current_phase is null
-- everywhere.

ALTER TABLE quizchef.sessions
    DROP CONSTRAINT IF EXISTS sessions_current_phase_check;

ALTER TABLE quizchef.sessions
    ADD CONSTRAINT sessions_current_phase_check
        CHECK (current_phase IN ('QUESTION_OPEN', 'QUESTION_CLOSED', 'ANSWER_REVEALED', 'LEADERBOARD'));
