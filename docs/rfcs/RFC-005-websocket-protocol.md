# RFC-005 Realtime Protocol

Status

Draft

Authors

Aman Minz

Created

2026-07-14

Updated

2026-07-16

---

# Summary

Placeholder. This RFC is a Draft: it names the scope below, and nothing in it is agreed.

---

# Scope

* STOMP topics
* Events
* Payloads
* Connection lifecycle (connections are ephemeral; participants are durable session entities)
* Domain event subscription (the WebSocket publisher subscribes to the event dispatcher and translates domain events onto STOMP topics; it never calls into domain modules)
* Reconnect flow and state synchronization payloads (current question, remaining time, submitted answer, score, leaderboard)
* Error handling
