# RFC-021 Resuming an Anonymous Participant

Status

Implemented

<!-- Draft | Proposed | Accepted | Implemented | Superseded by RFC-XXX
     Implemented — the whole scope shipped in one PR, the same precedent as
     RFC-010/011/013/014/015/016/017/018/019/020. See README.md for the lifecycle. -->

Authors

Aman Minz

Created

2026-08-16

Updated

2026-08-16

---

# Summary

An anonymous player who drops out — a refresh, a sleeping phone, a walk out of Wi-Fi range,
a browser closed and reopened — comes back as the *same* participant, with the same score,
answers, name, and language. Identity is proved by a server-issued **resume token**, never by
a display name; the token is now stored hashed, verified against one session, and presented
to a resume endpoint that runs *before* any join. A name is reserved for the participant
holding it, so a second "Aman" is refused rather than silently created with nobody's score.

This amends decisions recorded in **[RFC-004](RFC-004-session-engine.md)** (the reconnect
endpoint and its command shape) and **[ADR-003](../adr/ADR-003-durable-participants.md)** (the
guest credential). Both remain correct in their reasoning; this document supersedes their
mechanics.

---

# Motivation

Durable participants have worked since the session engine shipped: disconnecting has never
deleted anyone, and reconnection has always restored score and answers. Four things were
still wrong, and the first is a live defect.

**1. A recycled PIN resumed players into the wrong quiz.** A session PIN is unique only among
*active* sessions — archive one and the code returns to the pool. The client stored a
player's credential keyed by **PIN**, and guest reconnect looked the token up **globally**,
ignoring which session was asked for. So a player returning to a familiar six digits presented
last month's credential and was restored into last month's finished session: no current
question, no score in tonight's game, and no obvious way out. This is the "loses participation
identity" symptom in its most confusing form, because everything appears to work.

**2. The credential was stored in the clear**, in two tables. A resume token is a bearer
credential — whoever holds it *is* that participant. Any dump, replica, backup, or
over-shared query result handed out live identities for every guest in every session that had
ever run.

**3. A token was not bound to a session.** Presenting session A's token while asking about
session B resumed A, because the session in the request was ignored for guests.

**4. Two players could take the same name**, which made losing a credential unreadable: the
returning owner of "Aman" silently became a second "Aman" with zero points, and the room saw
two identical rows on the leaderboard.

---

# Goals

- The same participant returns, with score, answers, name, and language intact, after any
  interruption.
- Identity is proved by a secret the server issued, never by a name and never by an id.
- The credential is unusable if the database leaks.
- A credential works for exactly one session, and only while that session is live.
- Concurrent returns converge on one participant with no duplicate roster entry.

---

# Non Goals

- **Host-assisted recovery.** A "Generate Resume Code" action that moves a participant to a
  new device is a real need for a player who lost their storage, but it is a new host-facing
  capability with its own authorization and its own abuse surface. The brief said to build it
  only if small and clearly safe; it is neither. Recorded in *Future Work*.
- **Token expiry or rotation.** A token's useful life is one session's, and a session's own
  lifecycle already bounds it: once archived, its credentials resolve nowhere.
- **Surviving a closed Incognito window.** Private browsing destroys storage on exit by
  design. No application can prevent it, and pretending otherwise would mean falling back to
  the name — the exact thing this RFC forbids.

---

# Proposed Design

## The credential

`GuestParticipantToken` is the raw secret and is **no longer persistable** — it has no JPA
mapping at all, so it cannot reach the database by accident. What is stored is
`GuestTokenDigest`, the SHA-256 of the token, in both `participants.guest_token` and the
roster's `session_participants.guest_token` (the `ParticipantKey` mirror that enforces "one
guest token per session").

No salt and no slow KDF, deliberately. Those defend passwords — low-entropy, reused, and
guessable. This is 256 bits the server itself generated: there is no dictionary to attack, so
a plain digest is enough and keeps the lookup an ordinary indexed equality.
`GuestTokenDigest.matches` still compares in constant time, because the cost is nil and it
puts the authorization decision somewhere a reader can find it.

The raw token exists in exactly two places: the join response that issues it, and the resume
request that presents it. It is returned **once** — the server keeps only a digest, so it
cannot re-issue one even if asked, which is the point. `toString` is overridden to redact,
so a token cannot reach a log line through an exception message or a debugger.

## Resume, addressed by PIN

```text
POST /api/v1/sessions/{pin}/participants/resume     { resumeToken }
        ↓ resolve the ACTIVE session for this PIN
        ↓ find the participant in THAT session, by digest
        ↓ verify (constant-time) and connect
        ↓ snapshot: phase, question, remaining time, own answer, score, name, language
```

Replaces `POST /api/v1/sessions/reconnect`, which was global and ignored the session.

**Addressed by PIN rather than by session id, which deviates from the brief's URL and is the
crux of the fix.** The player's only handle is the PIN; the session id they have stored is
precisely the stale value that caused the bug. Resolving the PIN server-side means a
credential left over from an archived session that reused those digits does not resolve —
the player is told they are not in this session and can join it, instead of being restored
into a finished game. Addressing by the stored session id would have preserved the defect
behind a new URL.

Two guards fall out of the same lookup: a token issued for a *different live* session is not
in this session and cannot be replayed across quizzes; and a participant id is never accepted
as proof, because it travels in URLs and in this session's own responses.

The session row is write-locked for the same reason joining locks it: two tabs, or a
reconnect racing a refresh, resume the same participant at once. Serialized they converge;
unserialized one loses an optimistic check and the player is shown an error for doing
nothing. Resume never writes the session's own phase, so it cannot roll a game backwards.

## Name reservation

Join refuses a display name already present in the session, case- and whitespace-insensitively
(`participant.name-already-taken`), checked under the write lock join already holds — so
there is no window for a second "Aman" between the check and the insert.

Two deliberate limits. **No database constraint backs it:** sessions that ran before this rule
may legitimately contain duplicates, and renaming people in finished quizzes to satisfy a
unique index would rewrite what those events looked like. The lock is the authority for every
session that can still be joined, which is the only kind the rule applies to. And **the rule
never blocks a resume**, which resolves by token and never reads a name at all.

The player-facing message says the name is taken and to ask the Quiz Master — never "try
another name", because the likeliest reader is the real owner returning on a device that lost
its storage, and the one thing we must not do is let them in on the strength of the name.

## Client storage

Keyed by **session id**, with a `pin → sessionId` hint to find the candidate record. The hint
is not an authority: the server re-resolves the PIN and rejects a credential that does not
belong, so a stale hint costs one rejected resume and nothing else. Stored under a new key
(`quizchef.playerSession.v2`); v1 records are dropped rather than migrated, since they are
exactly the PIN-keyed values that cannot be trusted.

Resume runs on every arrival and always before the join form is offered. A rejected
credential is forgotten rather than retried. Records are pruned when a session reaches
`ARCHIVED` — **not** at `FINISHED`, because a finished session is still being read: the
podium runs, the host releases results, and a player refreshes to see where they came.

---

# Alternatives Considered

**Match on display name when the token is missing.** The obvious "helpful" behaviour, and the
one the brief opens by forbidding. Anyone who can hear a name can take that person's score,
and at a church event every name in the room is read aloud.

**Keep the global token lookup and just add a session check.** Would have fixed cross-session
replay but not the recycled-PIN bug, because the client's stored session id is itself the
stale value. Resolving from the PIN is what makes the stale case detectable.

**Encrypt the token at rest instead of hashing it.** Reversible, so the key becomes the thing
to steal, and nothing in the system needs to read a token back — only to recognize one.

**Salted bcrypt/Argon2 for the token.** Right for passwords, wasted here: the value is
server-generated at full entropy, and a per-resume KDF would put a deliberate delay on the
hottest path in a live event.

**Let the client send its stored `participantId` as identification.** It is already in URLs
and API responses; treating it as a credential would make every leaked id a takeover.

---

# Risks

**A player who loses storage cannot recover.** By construction — the server cannot prove they
are who they say. They are told to ask the host, and the host has no tool yet. This is the
honest failure, but it is a real one at an event, and it is what *Future Work* is for.

**Name reservation can frustrate legitimate duplicates.** Two genuine Amans in one room must
now distinguish themselves. Accepted: a leaderboard with two identical rows is worse, and the
host reads names aloud.

**The rule is enforced by a lock, not a constraint.** Correct for every session that can be
joined; a direct database insert would bypass it. No path in the application does that.

---

# Migration

`V15__hash_guest_resume_tokens.sql` hashes existing rows **in place**, in both tables. Tokens
already sitting in players' browsers keep working, because the server hashes what is presented
and compares — so this ships mid-event without logging anybody out of a quiz they are playing.

`POST /api/v1/sessions/reconnect` is removed rather than deprecated: nothing outside this
repository consumes it, and two paths to the same state are how the two drift apart. The
`ParticipantCommand.Reconnect` STOMP command (model-only, never handled) becomes `Resume`
with the same PIN addressing.

The resume snapshot gained `displayName` and `preferredLanguage` so a returning device renders
what the server believes rather than what its own storage holds — the two can differ after a
device switch, and the server is the one that is right (ADR-006).

---

# Open Questions

None outstanding.

---

# Acceptance Criteria

- [x] An anonymous join issues a resume credential, exactly once.
- [x] Refresh, long disconnect, and reconnect all resume the same participant.
- [x] Score, answers, name, and language survive; an already-answered question cannot be
      answered twice.
- [x] Participant count and roster do not grow on resume, however many times it runs.
- [x] Simultaneous resumes converge — no duplicate, no error, no reset score.
- [x] An invalid token, another session's token, and a participant id are all refused.
- [x] A credential from an archived session does not resume into the session reusing its PIN.
- [x] The same name without a credential inherits nothing; a valid credential resumes rather
      than tripping the name rule.
- [x] The stored value is a digest — the issued token appears in neither table.
- [x] The token is never in a roster read, a history read, a snapshot, or a realtime event.

---

# Future Work

- **Host-assisted transfer.** A host-only action that mints a fresh single-use code for an
  existing participant, so a player whose storage is gone can be moved to a new device on the
  host's authority rather than on their own claim. The right shape for the failure this RFC
  leaves open.
- **A visible "Welcome back, Aman" on resume.** ADR-003 called for it; the data is now in the
  snapshot and nothing renders it yet.
- **Single active connection.** Still listed in RFC-004's Future Work and still transport
  work (RFC-005): resume deliberately allows a second device, since it cannot tell one from a
  refresh.
