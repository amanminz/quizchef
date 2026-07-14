# ADR-004

Decision: Domain state is durable. Transport state is ephemeral.
Status: Accepted
Reason: Nothing in the business domain may depend on a WebSocket connection. ADR-003 makes participants survive disconnects; this ADR generalizes the principle: the transport is an infrastructure detail, and coupling domain logic to it would make every transport change an architectural change.
Consequences: The session engine and all domain modules express realtime behavior as domain events and state, never as connection operations. Only the websocket module knows the transport exists; it subscribes to domain events and delivers them. WebSocket can disappear, and SSE, MQTT, or polling could replace it without the session engine caring. Connection identifiers never appear in domain models, application services, or the database schema beyond the participant's optional active-connection property defined in ADR-003. Builds on [ADR-003](ADR-003-durable-participants.md).
