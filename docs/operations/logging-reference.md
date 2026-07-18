# Logging Reference

Every structured log field, every operational event this system logs, and what must never appear in a log line. The authoritative design is [RFC-010](../rfcs/RFC-010-observability-and-operational-readiness.md); this is the lookup table.

---

## Format

Every log line is one JSON object (ECS — Elastic Common Schema), on stdout. Standard ECS fields (`@timestamp`, `log.level`, `log.logger`, `message`, `service.name`, `process.pid`, `process.thread.name`) are present on every line; the fields below are added via SLF4J MDC and appear only while set.

## MDC fields (present when applicable)

| Field | Set by | Present when |
|---|---|---|
| `correlationId` | `CorrelationIdFilter` (reused from `X-Correlation-Id` or generated) | Every request; also on the scheduled question-close job as a synthetic `timer-<uuid>` |
| `requestId` | `CorrelationIdFilter` | Every request — unique per attempt, even when `correlationId` is reused across a retry |
| `identityId` | `JwtAuthenticationFilter` | Once a bearer token authenticates successfully |
| `sessionId` | `RequestContextMdcInterceptor` | Any route with a session-scoped path variable (`/sessions/{id}/...`) |
| `quizId` | `RequestContextMdcInterceptor` | Any route with `{quizId}` |
| `questionId` | `RequestContextMdcInterceptor` | Any route with `{questionId}` |
| `operation`, `durationMs` | `RequestLoggingFilter` | Only on that filter's own one-line-per-request summary |

All MDC keys are cleared at the end of every request (`CorrelationIdFilter`'s `finally`), so nothing leaks across pooled request-handling threads.

## Per-request summary line

One INFO line per completed request (`RequestLoggingFilter`):

```text
METHOD /raw/path -> STATUS (DURATION ms)
```

`operation` in MDC on this line is `METHOD` + the *resolved route pattern* (e.g. `POST /api/v1/sessions/{id}/start`), not the raw path — so grouping by operation is stable across different session ids.

---

## Operational domain events logged

Every line below is INFO, one per event, logged by the named `platform`-module listener (or `SessionEventLogger`/`GameplayEventLogger` in `platform`, or the co-located logger in `websocket` where noted). Fields are ids and states only — never PII, never credentials.

| Logger | Event | Message | Fields |
|---|---|---|---|
| `IdentityEventLogger` | `IdentityRegisteredEvent` | `identity.registered` | `identityId`, `identityType` |
| | `IdentityAuthenticatedEvent` | `identity.login` | `identityId`, `identityType` |
| | `HostAccessGrantedEvent` | `identity.host_promoted` | `identityId`, `identityType` |
| `QuizEventLogger` | `QuizCreatedEvent` | `quiz.created` | `quizId`, `ownerId` |
| | `QuizPublishedEvent` | `quiz.published` | `quizId` |
| | `QuizArchivedEvent` | `quiz.archived` | `quizId` |
| | `QuestionAddedToQuizEvent` | `quiz.question_attached` | `quizId`, `questionId` |
| | `QuestionPublishedEvent` | `question.published` | `questionId` |
| | `QuestionArchivedEvent` | `question.archived` | `questionId` |
| `SessionEventLogger` | `SessionCreatedEvent` | `session.created` | `sessionId`, `hostId`, `publishedQuizVersionId` |
| | `LobbyOpenedEvent` | `session.lobby_opened` | `sessionId` |
| | `SessionStartedEvent` | `session.started` | `sessionId` |
| | `SessionFinishedEvent` | `session.finished` | `sessionId` |
| `GameplayEventLogger` | `QuestionStartedEvent` | `gameplay.question_opened` | `sessionId`, `questionId`, `endsAt` |
| | `QuestionClosedEvent` | `gameplay.question_closed` | `sessionId`, `questionId` |
| | `AnswerRevealedEvent` | `gameplay.answer_revealed` | `sessionId`, `questionId`, `correctOptionCount` (a count, never the option ids' meaning beyond that) |
| | `LeaderboardUpdatedEvent` | `gameplay.leaderboard_shown` | `sessionId`, `standingsCount` (never per-participant scores) |
| `ParticipantEventLogger` | `ParticipantJoinedEvent` | `participant.joined` | `sessionId`, `participantId` |
| | `ParticipantReconnectedEvent` | `participant.reconnected` | `sessionId`, `participantId` |
| | `ParticipantDisconnectedEvent` | `participant.disconnected` | `sessionId`, `participantId` |
| | `AnswerSubmittedEvent` | `participant.answer_submitted` | `sessionId`, `participantId`, `questionId` |

**Deliberately not logged**: `IdentityAuthorizedEvent` (fires on every permission check — request noise, not a transition).

---

## Never log

- Passwords, password hashes.
- JWTs, refresh tokens, guest reconnection tokens.
- Email verification tokens.
- Answer correctness before `ANSWER_REVEALED` (structurally enforced — `AnswerSubmittedEvent` carries no correctness field at all; `AnswerRevealedEvent`, logged only once revealed, is the first point correctness is safe to log).
- Full leaderboard standings with participant scores (only a count is logged; the results read is the source of truth for actual standings).
- Email addresses or any other personally-identifying field — every identity domain event carries only an `IdentityReference` (id + type) by design, never PII.
