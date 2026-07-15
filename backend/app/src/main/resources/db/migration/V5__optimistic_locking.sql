-- Optimistic locking for every aggregate table: concurrent saves lose
-- against the version column (JPA @Version on AuditableEntity) instead of
-- silently overwriting each other. Existing rows start at version 0.

ALTER TABLE quizchef.identities        ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE quizchef.credentials       ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE quizchef.user_profiles     ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE quizchef.identity_sessions ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE quizchef.quizzes           ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE quizchef.questions         ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
