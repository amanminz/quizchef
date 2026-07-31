-- Adds the final-results hold: a session finishes without releasing final
-- standings to participants until the host explicitly reveals them (the
-- winner ceremony gate). Sessions that finished before this feature existed
-- are backfilled as already-released, since nobody is waiting on a ceremony
-- for an event that already happened.

ALTER TABLE quizchef.sessions ADD COLUMN final_results_released BOOLEAN;

UPDATE quizchef.sessions
SET final_results_released = TRUE
WHERE state IN ('FINISHED', 'ARCHIVED');

UPDATE quizchef.sessions
SET final_results_released = FALSE
WHERE final_results_released IS NULL;

ALTER TABLE quizchef.sessions ALTER COLUMN final_results_released SET NOT NULL;
ALTER TABLE quizchef.sessions ALTER COLUMN final_results_released SET DEFAULT FALSE;
