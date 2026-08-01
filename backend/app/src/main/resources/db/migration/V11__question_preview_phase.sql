-- Adds QUESTION_PREVIEW to the gameplay phase set: a short, server-timed
-- reading period before a question opens for answers. Additive; no data
-- migration needed (no session currently in current_phase='...' would
-- violate the widened constraint — it only adds a permitted value).

ALTER TABLE quizchef.sessions
    DROP CONSTRAINT IF EXISTS sessions_current_phase_check;

ALTER TABLE quizchef.sessions
    ADD CONSTRAINT sessions_current_phase_check
        CHECK (current_phase IN
            ('QUESTION_PREVIEW', 'QUESTION_OPEN', 'QUESTION_CLOSED', 'ANSWER_REVEALED', 'LEADERBOARD'));
