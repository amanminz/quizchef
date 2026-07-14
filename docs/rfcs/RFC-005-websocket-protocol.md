## RFC-005

Realtime Protocol

Defines:

* STOMP topics
* Events
* Payloads
* Connection lifecycle (connections are ephemeral; participants are durable session entities)
* Domain event subscription (the WebSocket publisher subscribes to the event dispatcher and translates domain events onto STOMP topics; it never calls into domain modules)
* Reconnect flow and state synchronization payloads (current question, remaining time, submitted answer, score, leaderboard)
* Error handling
