# RFC-005 Realtime Protocol

Status

Accepted

<!-- Draft | Proposed | Accepted | Implemented | Superseded by RFC-XXX
     Accepted — the protocol and transport foundation (M4 PR #1.5) are
     implemented; command handling and gameplay projections follow.
     Flips to Implemented when realtime is feature-complete.
     See README.md for the lifecycle. -->

Authors

Aman Minz

Created

2026-07-14

Updated

2026-07-16

---

# Summary

Defines QuizChef's realtime messaging: a versioned, transport-independent **wire protocol** (a message envelope, a stable event/command vocabulary, a topic hierarchy, and a reconnection-snapshot contract) and the **STOMP-over-WebSocket adapter** that carries it. The Session engine (RFC-004) expresses realtime behaviour purely as domain events; this module subscribes to them and projects them onto the wire, and will delegate inbound commands to application services — it never contains business logic (ADR-004, ADR-005).

Milestone 4 PR #1.5 implements the **foundation**: the protocol types, topics, the outbound port + STOMP adapter, and the projection of the session domain events that exist today. It does **not** implement command handlers, timers, gameplay, scoring, leaderboards, or replay *generation* — only their protocol contracts. Accepted; flips to Implemented when realtime is feature-complete.

---

# Motivation

Live play is realtime, and realtime is where transport churn usually leaks into business code. ADR-004 forbids that leak; this RFC makes it concrete. It also sets the wire contract that every future client — the React web app first, but plausibly mobile apps and third-party integrations — builds against, so it is worth making clean, versioned, and stable from day one rather than retrofitting compatibility later.

---

# Goals

- A transport-independent protocol: the Session domain never knows STOMP, WebSocket, destinations, or connections exist.
- A **versioned** wire format, so clients and server evolve independently.
- A **stable vocabulary** decoupled from internal class names, so refactoring the domain never breaks a client.
- Centralized topics and serialization; no string literals or domain entities on the wire.
- A complete-enough contract (events, commands, reconnection snapshot) that Session APIs (PR #2) and gameplay (RFC-006) plug in without reshaping it.

---

# Non Goals

- Command handlers / `@MessageMapping` — inbound handling delegates to application services (Session APIs, PR #2).
- Timers, question progression, answer submission, scoring, leaderboards — gameplay (RFC-006).
- Replay/snapshot **generation** (`SessionRecoveryService`) — only the contract is defined here.
- Presence and connection management beyond the durable-participant model (ADR-003).
- An external message broker — the simple in-memory broker suffices at church scale (a relay is a deployment change, RFC-008).

---

# Proposed Design

## The flow, and the one rule

```text
Application Service → Session Aggregate → Domain Event → RealtimePublisher (port) → STOMP Adapter → Clients
```

Strictly one-directional outbound, and the inbound mirror (client command → application service → aggregate → domain event → …) is the same shape. The rule that makes it safe: **the transport is downstream of the domain, always.** Never `WebSocket → Session`, never `Controller → Session Aggregate`. The websocket module subscribes to domain events and speaks the protocol; it decides nothing (ADR-005).

## Transport-independent envelope

Every message, inbound or outbound, travels in `ProtocolMessage`:

```text
protocolVersion   the wire protocol version (not the app version)
messageId         unique per message
sessionId         which session; the topic routing key
occurredAt        when the underlying fact happened
type              the stable wire vocabulary
payload           type-specific data, or absent
```

**Protocol versioning from day one.** `protocolVersion` (currently `1`) rides on every message. It costs one integer and buys the freedom to evolve the protocol — new fields, new message types, a new replay format — while older web, mobile, or third-party clients keep working, and the server can serve more than one version at once. It is bumped only on a breaking wire change; it is not the application version.

## Stable vocabulary, decoupled from class names

`type` is a dotted, lowercase, language-agnostic string — `participant.reconnected`, `session.started` — **never** the domain class name. `ProtocolMessageType` centralizes the vocabulary; a domain `ParticipantReconnectedEvent` maps to the wire name `participant.reconnected`, so renaming or repackaging a domain class can never break a client, and clients across languages share one clean vocabulary.

Vocabulary (this PR projects the first group; the rest are reserved, their producers/payloads arriving with gameplay):

```text
lobby.opened              participant.joined          question.started     (reserved)
session.started           participant.disconnected    question.closed      (reserved)
session.finished          participant.reconnected     answer.revealed      (reserved)
                                                       leaderboard.updated  (reserved)
session.snapshot          (reconnection replay)
```

## Transport events are projections, not domain events

A protocol event is a **projection** of a domain event, not the same thing. The domain event is the internal fact (`SessionStartedEvent`); the protocol message is its public, versioned, stable representation on the wire. They differ deliberately: the domain event can carry things clients must never see and can change shape as the model evolves; the projection is the frozen contract. `SessionProtocolMapper` is that projection seam. `SessionCreatedEvent` is intentionally **not** projected — it happens before anyone has connected, so it has no audience.

## Commands (definitions only)

The inbound vocabulary is defined now, handled later. Host commands: `OpenLobby, StartSession, StartQuestion, RevealAnswer, ShowLeaderboard, FinishSession`. Participant commands: `JoinSession, SubmitAnswer, Reconnect`. Per ADR-005 each will be validated at the transport edge and delegated to an application service; no handlers, no `@MessageMapping` exist yet.

## Topic hierarchy

Centralized in `Topics` — the only place a destination string is built. All under the `/topic` broker prefix:

```text
/topic/session/{sessionId}       broadcast to everyone in a session
/topic/participant/{participantId}  one participant (reconnection snapshot, private feedback)
/topic/host/{sessionId}          the host's control channel
/topic/system                    server-wide notices
```

The outbound port names the audience, never the destination:

```text
RealtimePublisher.publish(message)                     → /topic/session/{sessionId}
RealtimePublisher.publishToParticipant(id, message)    → /topic/participant/{id}
RealtimePublisher.publishToHost(sessionId, message)    → /topic/host/{sessionId}
```

`StompRealtimePublisher` is the only class that resolves a topic and touches `SimpMessagingTemplate`.

## Reconnection replay (contract only)

`session.snapshot` carries everything a reconnecting participant needs to resume (ADR-003, PRD "Welcome back"): session state, current question and phase, remaining time, the participant's score and submitted answer, and the leaderboard (`SessionSnapshot`). This PR defines the **shape**; generating it — reading the aggregates, computing remaining time and standings — is `SessionRecoveryService`, a later PR. State and phase cross as strings, not the session enums, so the protocol leaks no internal types.

## Synchronization primitives

`messageId`, `sessionId`, and `occurredAt` on every message are the basis for future duplicate detection, ordering, and idempotency. No such logic is implemented here — the fields exist so it can be added without a protocol break.

## Serialization & configuration

Jackson, ISO-8601 timestamps, `type` via `@JsonValue`. Only protocol DTOs are serialized — never a domain entity; domain state is always mapped into a payload. `WebSocketStompConfiguration` wires the endpoint (`/ws`, SockJS fallback), the simple broker (`/topic`), and the inbound prefix (`/app`, reserved for future command handlers). The `/ws/**` handshake is public; per-message authorization (who may subscribe to which session, who may command) arrives with Session APIs.

---

# Alternatives Considered

**Exposing domain event class names on the wire** — rejected: couples every client to internal Java naming and package structure. A stable dotted vocabulary (`participant.reconnected`) is language-agnostic and refactor-proof (Aman's recommendation).

**No protocol version field** — rejected: the first breaking change would break every deployed client at once. One integer now buys independent evolution (Aman's recommendation).

**Serializing domain entities / events directly** — rejected: leaks internal shape and data, and welds the wire format to the model. Always map to a protocol DTO.

**Handling commands in the websocket module** — rejected (ADR-005): the transport must not orchestrate business logic. Commands delegate to application services.

**An external broker (Kafka/RabbitMQ) now** — rejected: the in-memory simple broker fits single-instance church-scale deployments; a relay is a deployment change (RFC-008), not an architectural one.

**Scattered topic strings** — rejected: one `Topics` class means the hierarchy changes in one file.

---

# Risks

- The simple in-memory broker does not fan out across instances; horizontal scaling needs a broker relay (deployment, RFC-008). Fine for the target.
- Reserved gameplay vocabulary is defined without payloads; the gameplay PR must define those payloads against real domain events, not guess now.
- `/ws/**` is currently open; message-level authorization is deferred to Session APIs and must land before real sessions carry sensitive data.

---

# Migration

No schema or data changes — this PR is additive infrastructure and protocol types. The websocket module gains a dependency on the session module (to subscribe to its events); no other module changes except the public-endpoint whitelist (`/ws/**`).

---

# Open Questions

- **Per-message authorization** — subscribe/command authorization at the STOMP layer (a `ChannelInterceptor` consulting `AuthorizationService`) vs. at the application service. Decided with Session APIs (PR #2).
- **User-destination vs. explicit participant topics** — `/topic/participant/{id}` vs. STOMP `/user` destinations for private messages. Revisit when reconnection delivery is implemented.
- **Snapshot on the socket vs. a REST endpoint** — deliver the reconnection `SessionSnapshot` over STOMP or fetch it via REST on reconnect. Decided with `SessionRecoveryService`.

---

# Acceptance Criteria

- [x] The Session domain remains transport-independent; no domain depends on the websocket module (ArchUnit).
- [x] STOMP is an infrastructure adapter only; the outbound port carries no transport types.
- [x] Protocol messages defined: versioned envelope, stable vocabulary, event payloads, command definitions, reconnection-snapshot contract.
- [x] Topic naming centralized; no destination string literals elsewhere.
- [x] Session domain events project cleanly onto protocol messages (unit + integration).
- [x] No gameplay, scoring, timers, or command handlers.
- [x] All tests pass (mapping, topics, serialization, projection, Spring wiring through the adapter).

---

# Future Work

- **Session APIs (PR #2)**: inbound command handlers delegating to application services; per-message authorization.
- **Gameplay & scoring (RFC-006)**: the reserved `question.*`, `answer.revealed`, `leaderboard.updated` projections against real gameplay domain events.
- **`SessionRecoveryService`**: generate the `session.snapshot` (score, answers, current question, remaining time, leaderboard) and deliver it on reconnect.
- **Idempotency / ordering**: use `messageId` and `occurredAt` for duplicate detection and client-side ordering.
- **Broker relay (RFC-008)**: an external broker for multi-instance fan-out if deployment scale demands it.
