# Operations

Day-2 documentation for running QuizChef in production. Where the [RFCs](../rfcs/) explain *why* the system is built the way it is, these documents explain *what to do* when it's running.

The full design behind everything referenced here is [RFC-010 Observability and Operational Readiness](../rfcs/RFC-010-observability-and-operational-readiness.md).

---

# Index

- [Runbook](runbook.md) — day-2 operational procedures: starting, stopping, checking health, common maintenance tasks.
- [Incident Response](incident-response.md) — how to triage a production issue using correlation ids, logs, metrics, and health.
- [Monitoring](monitoring.md) — what to watch: every health component and metric, and what a bad value means.
- [Production Checklist](production-checklist.md) — what must be true before a production deploy is safe.
- [Deployment Checklist](deployment-checklist.md) — the steps of a deploy itself.
- [Logging Reference](logging-reference.md) — every structured log field, every operational event logged, and what must never be logged.

---

# Scope

These documents describe the operational surface that exists today: structured logging, correlation, backend-owned metrics, health/readiness/liveness. They deliberately do not cover distributed tracing, external monitoring vendors, alerting rules, or load testing — those are later operational phases (RFC-010's Non Goals).
