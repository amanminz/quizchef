# RFCs

An RFC records **how a major feature is designed, and why** — the reasoning behind a decision, kept after the decision is made.

Every RFC carries a `Status`. It answers the first question any reader has: **does this document describe a plan, or the architecture that exists today?**

---

# Index

- [RFC-001 Repository Foundation](RFC-001-repository-bootstrap.md) — **Implemented**
- [RFC-002 Identity and Access](RFC-002-identity-and-access.md) — **Implemented**
- [RFC-003 Quiz Authoring](RFC-003-quiz-authoring.md) — **Implemented**
- [RFC-004 Session Engine](RFC-004-session-engine.md) — **Implemented** (domain, orchestration, and gameplay execution engine)
- [RFC-005 Realtime Protocol](RFC-005-websocket-protocol.md) — **Accepted** (protocol + all outbound projections implemented; inbound STOMP command channel follows)
- [RFC-006 Scoring Engine](RFC-006-scoring-engine.md) — **Implemented**
- [RFC-007 Media](RFC-007-media-storage.md) — **Draft**
- [RFC-008 Deployment](RFC-008-deployment.md) — **Draft**
- [RFC-009 Frontend Architecture](RFC-009-frontend-architecture.md) — **Implemented** (React platform foundation; feature UIs build on it)
- [RFC-010 Observability and Operational Readiness](RFC-010-observability-and-operational-readiness.md) — **Implemented** (correlation, structured logging, domain event logging, metrics, health/readiness, sanitized errors)
- [RFC-011 Security Hardening and Abuse Prevention](RFC-011-security-hardening-and-abuse-prevention.md) — **Implemented** (security headers, CORS, rate limiting, STOMP destination validation, input validation, secrets audit)
- RFC-012 Performance and Scalability — **Draft** (number reserved for Phase 3 PR #4, planned; not yet written)
- [RFC-013 Question Authoring and Quiz Composition](RFC-013-question-authoring-and-quiz-composition.md) — **Implemented** (question editor, Question Library page, quiz-launched authoring with auto-attach; frontend-only — the RFC-003 APIs were already complete)

---

# Lifecycle

```text
Draft → Proposed → Accepted → Implemented
                                   │
                                   ▼
                              Superseded
```

**Draft**

Being written, or a placeholder naming a feature we know is coming. Nothing is agreed and nothing is committed to. A stub listing bullet points is a Draft.

**Proposed**

Complete and under review. The design is finished enough to argue with: the trade-offs are stated, the alternatives are recorded, and the open questions are named. This is the moment to disagree — it is cheaper here than anywhere downstream.

**Accepted**

Agreed. The design is decided but the code does not exist yet, or does not exist fully. **An Accepted RFC describes a plan.** An RFC implemented across several PRs stays Accepted until the last of them merges.

**Implemented**

The code matches this document. **An Implemented RFC describes the current architecture** and can be trusted as such. Items the RFC lists under *Future Work* are by definition outside its scope and do not hold it back.

**Superseded**

Replaced by a later RFC. The document stays exactly where it is, unedited, because the reasoning is the point: a future contributor needs to know not just what we do now, but what we used to do and why we changed. Record `Superseded by RFC-XXX` in the status, and have the successor say what it supersedes.

---

# Rules

**Status changes in the PR that causes it.** The PR that finishes implementing an RFC flips it to Implemented, in the same commit as the code. Documentation moves with the code that makes it true — the same discipline the Definition of Done already asks for.

**Do not edit an Implemented RFC to describe a new design.** Corrections, clarifications, and links are welcome; new decisions are not. When a decision changes, write a new RFC and supersede the old one. Rewriting history to match the present destroys the only record of why the present looks like this.

**One RFC per decision worth arguing about.** If a change needs a paragraph in an existing RFC, put it there. If it needs a design discussion — new aggregates, a new lifecycle, a new failure mode — it earns its own RFC. Question Revisions is the current example: it changes what a published quiz points at, so it is an RFC, not a footnote on RFC-003.

**Reasoning outlives status.** Alternatives Considered is not a formality. It is the section that stops the project from relitigating a settled question every six months, and it is the reason a Superseded RFC is still worth reading.
