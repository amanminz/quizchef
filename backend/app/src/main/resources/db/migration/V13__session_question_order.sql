-- A session's own question order.
--
-- Published quizzes are immutable (Quiz.reorder refuses once PUBLISHED) and
-- sessions pin the version they execute, so the same quiz played twice runs
-- the same sequence. That is correct for the content and wrong for the
-- event: a group replaying a quiz should not get the questions in the order
-- they already remember.
--
-- So the order a session plays belongs to the session, not to the quiz.
-- Empty means "the quiz's authored order", which is what every existing
-- session has and what a session gets unless the host shuffles it.
CREATE TABLE IF NOT EXISTS quizchef.session_question_order (
    session_id  UUID    NOT NULL REFERENCES quizchef.sessions (id) ON DELETE CASCADE,
    question_id UUID    NOT NULL,
    position    INTEGER NOT NULL,
    PRIMARY KEY (session_id, position),
    CONSTRAINT session_question_order_unique_question UNIQUE (session_id, question_id)
);
