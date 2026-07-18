# Monitoring

Every health component and metric this system exposes, and what a bad value means. No dashboard or alerting tool is wired up yet (out of scope for RFC-010) — this is the reference for building one, or for reading `/actuator/*` by hand.

---

## Health components (`/actuator/health`)

| Component | Source | UP means | DOWN means |
|---|---|---|---|
| `db` | Spring Boot auto-configured (`DataSource` on classpath) | Postgres reachable | Postgres unreachable, pool exhausted, or misconfigured |
| `realtime` | `RealtimeHealthIndicator` (websocket module) | The STOMP broker (`simpleBrokerMessageHandler`) reports `isBrokerAvailable()` | The broker stopped or never started — a JVM-level problem, not typically network |
| `diskSpace` | Spring Boot default | Enough free disk for the configured threshold | Disk pressure on the host |

`/actuator/health/readiness` and `/actuator/health/liveness` are the same aggregate, scoped to the probe groups Spring Boot wires from `management.endpoint.health.probes.enabled=true` — point a container orchestrator's readiness/liveness probes at these, not `/actuator/health` itself, which may include components not appropriate for probe semantics later.

---

## Metrics (`/actuator/metrics/{name}`, auth required)

### HTTP (built into Spring Boot — no custom code)

- `http.server.requests` — tags `uri`, `method`, `status`, `outcome`. Watch `outcome=SERVER_ERROR` by `uri` for regressions.

### Realtime (`websocket` module — transport-level)

- `realtime.connections.opened` (counter) — every STOMP CONNECT, first or reconnect (not distinguished — see RFC-010's noted limitation).
- `realtime.connections.active` (gauge) — currently open STOMP sessions.
- `realtime.subscriptions.active` (gauge) — currently active topic subscriptions across all connections.

### Sessions (`platform` module — domain-level)

- `session.active` (gauge) — sessions between `SessionCreatedEvent` and `SessionFinishedEvent`.
- `session.completed` (counter) — total finished sessions since process start.
- `session.duration` (timer) — wall-clock time from creation to finish.
- `session.participants` (distribution summary) — participant count at the moment a session finished.

### Gameplay (`platform` module)

- `gameplay.answers_submitted` (counter).
- `gameplay.answer_latency` (timer) — time from a question opening to each accepted answer.
- `gameplay.reconnect_recovery` (counter) — domain-level participant reconnections (distinct from `realtime.connections.opened`, which is transport-level).

### Identity (`platform` module)

- `identity.registrations`, `identity.logins`, `identity.host_promotions` (counters).

---

## What "normal" looks like at church scale

These metrics were sized for the project's actual deployment target (Bible Quiz events, tens to low hundreds of concurrent participants), not internet scale. A `session.participants` distribution in the tens, `realtime.connections.active` tracking the same, and `http.server.requests` p99 in the tens of milliseconds against a local Postgres are the expected baseline — there is no load-tested ceiling documented yet (Phase 3's later performance work).
