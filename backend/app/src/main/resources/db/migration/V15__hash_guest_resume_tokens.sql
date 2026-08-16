-- Store the SHA-256 of a guest's resume token, never the token itself.
--
-- The resume token is a bearer credential: whoever holds it is that
-- participant, with their score and their answers. Held in the clear it sat
-- in two tables, and therefore in every dump, replica, backup, and
-- over-shared query result -- a live identity for every guest in every
-- session that has ever run.
--
-- 256 bits of server-generated entropy needs no salt and no slow KDF: there
-- is no dictionary to reverse and no password reuse to protect. A plain
-- digest is enough, and it keeps the lookup an ordinary indexed equality.
--
-- Existing rows are hashed IN PLACE rather than invalidated. A token already
-- sitting in a player's browser keeps working, because the server hashes
-- what is presented and compares -- so this can ship mid-event without
-- logging anybody out of a quiz they are playing.

-- The participant's own credential.
UPDATE quizchef.participants
SET guest_token = encode(sha256(guest_token::bytea), 'hex')
WHERE guest_token IS NOT NULL;

-- The same value inside the session's roster (ParticipantKey), which mirrors
-- it to enforce "one guest token per session". Both must move together or
-- the roster stops recognizing its own participants.
UPDATE quizchef.session_participants
SET guest_token = encode(sha256(guest_token::bytea), 'hex')
WHERE guest_token IS NOT NULL;
