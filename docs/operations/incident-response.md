# Incident Response

How to triage a production issue using what RFC-010 gives you: correlation ids, structured logs, metrics, and health.

---

## 1. Is it the whole system, or one request?

Check `/actuator/health` first (see the [Runbook](runbook.md)). `DOWN` on `db` or `realtime` means the whole system is affected — jump to §3. A healthy aggregate with a user-reported error means it's request-scoped — go to §2.

## 2. One request: start from the correlation id

Every error response — including the generic `internal.error` for something truly unexpected — carries a `correlationId`. The frontend shows it in small print under any error panel, and prominently in the fatal-error dialog (`ErrorBoundary`). Ask the reporter for it, or find it in a support ticket screenshot.

```bash
docker compose logs backend | jq -R 'fromjson? | select(.correlationId == "<id>")'
```

This returns *every* log line the request produced, across every module it touched — the request-entry line (`RequestLoggingFilter`, with method/route/status/duration), any operational domain-event lines it triggered, and the `GlobalExceptionHandler` line if it failed. Read them in order; the last one is usually the actual failure.

If the correlation id starts with `timer-`, the failure happened inside the scheduled question-close job, not a request — there is no user-facing correlation id to hand back, but the same log-grouping technique works for diagnosing the timer firing itself.

## 3. System-wide: use health and metrics together

- `db` component `DOWN` → Postgres is unreachable or out of connections. Check `docker compose ps postgres` and the Hikari pool metrics (`/actuator/metrics/hikaricp.connections.active`, `.../hikaricp.connections.pending`).
- `realtime` component `DOWN` → the STOMP broker reported `isBrokerAvailable() == false`. With the in-process simple broker (current deployment), this should be rare and points at a JVM-level problem, not a network one.
- Elevated `http.server.requests` error rate (tag `status=5xx`) without a specific correlation id in hand → look for a spike in one route (`uri` tag) and pull recent `internal.error` lines from the logs for that route's correlation ids.
- `session.active` stuck non-zero long after an event should have ended → a session never received a `SessionFinishedEvent`; check for a host who never advanced past the last question, not a system fault.

## 4. After resolving

There is no incident-tracking system integrated yet (out of scope for this RFC). Record the correlation id(s), root cause, and fix in whatever the team's actual incident log is — this document only covers *finding* the information, not recording the outcome.
