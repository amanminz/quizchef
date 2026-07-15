# QuizChef Architecture

**Version:** 1.0.0

**Status:** Living Document

**Last Updated:** 2026-07-14

---

# 1. Purpose

This document is the architectural source of truth for QuizChef.

Every contributor (human or AI) MUST read this document before making architectural decisions.

This document explains:

- Why QuizChef exists
- What problems it solves
- Architectural philosophy
- Engineering principles
- Module boundaries
- System design
- Constraints
- Coding philosophy

Implementation details belong in their respective documents.

---

# 2. Vision

QuizChef is an open-source platform for building and hosting engaging real-time quizzes.

The first deployment powers Bible Quiz events for Bangalore Evangelical Lutheran Church.

The long-term vision is to become an extensible quiz platform for churches, schools, companies, conferences and communities.

QuizChef should feel as simple as Kahoot while remaining completely self-hostable.

---

# 3. Mission

Enable anyone to create engaging quizzes in minutes.

Provide an exceptional live experience for participants.

Remain easy to deploy, maintain and extend.

---

# 4. Design Goals

Priority order.

1. Simplicity
2. Reliability
3. Maintainability
4. Extensibility
5. Performance
6. Scalability

Scalability should never come at the cost of developer productivity unless necessary.

---

# 5. Engineering Principles

QuizChef follows these principles.

## 5.1 Modular Monolith

QuizChef is NOT a microservice application.

It is a modular monolith.

Reasons:

- Faster development
- Easier deployment
- Easier debugging
- Lower infrastructure cost
- Better developer experience

Modules should behave like independent services while sharing one deployment.

---

## 5.2 Domain First

The domain model drives the architecture.

Controllers do not define the system.

Database tables do not define the system.

The business domain defines the system.

---

## 5.3 API First

Public APIs are contracts.

Controllers should never expose entities.

Every endpoint should be intentionally designed.

---

## 5.4 Open Source First

Every architectural decision should assume:

Someone else will read this code.

Someone else will contribute.

Someone else will deploy it.

Code should be optimized for readability.

---

## 5.5 Convention Over Configuration

Reasonable defaults should exist.

Users should configure only what they must.

---

## 5.6 Testability First

Every service should be testable.

Every module should be independently testable.

Dependencies should be injectable.

---

## 5.7 Security by Default

Authentication is mandatory where required.

Authorization is explicit.

Sensitive information is never logged.

Validation occurs at the API boundary.

---

## 5.8 Feature Completeness

Shipping fewer polished features is preferred over shipping many incomplete ones.

---

# 6. Architecture Overview

                   React Application

                          │

          REST API + WebSocket (STOMP)

                          │

          Spring Boot Modular Monolith

┌─────────────────────────────────────────────┐

App

Identity

User

Quiz

Session

Media

Security

WebSocket

Common

└─────────────────────────────────────────────┘

                PostgreSQL

                     │

                  MinIO

---

# 7. High Level Components

Frontend

Responsible for:

- UI
- Routing
- Authentication
- WebSocket Client
- State Management

Backend

Responsible for:

- Business Logic
- Validation
- Authentication
- Authorization
- Session Management
- Scoring

Database

Responsible for:

- Persistence

Object Storage

Responsible for:

- Images
- Audio
- Video

---

# 8. Core Domain Model

The identity hierarchy:

Identity

↓

User

↓

Participant

## Identity

Who someone is.

Every actor in the system has an Identity.

An Identity is either a Guest Identity or a Registered User.

Guest Identities are short-lived and exist only to play.

## User

A registered Identity.

Represents:

Authentication

Registration

Profile

Roles

Email

Password

Owned by the Identity module (with user-facing features in the User module).

## Participant

Someone playing in a live session.

Represents:

Joining a quiz

Display name

Total score (cached sum of answer points)

Connection status

Active connection (optional)

Session

Ranking

Answers

Owned by the Session module.

A Participant is always backed by an Identity — either a Guest Identity or a Registered User.

Modules refer to an Identity through IdentityReference (identityId + identityType) — who is acting, never credentials, profile, or sessions.

A Participant is scoped to a single session. The same person joining two sessions is two Participants.

Guest play and registered play share one code path.

## Durable Participants

A Participant is a durable session entity. Connections are ephemeral.

A player joins a quiz session, not a WebSocket connection. The active connection is an optional property of the Participant, never its identity.

The active connection is modeled as a ParticipantConnection: active transport, reconnect, heartbeat, disconnect. It is deliberately not called "presence" — presence has a distinct meaning in distributed systems.

A Participant survives network interruptions, browser refreshes, app crashes, device sleep, and network switches.

Disconnects mark the Participant disconnected. They never delete it.

Participant lifecycle:

Created

↓

Connected

↓

Disconnected

↓

Reconnected

↓

Finished

Reconnection restores score, answers, current question, and leaderboard position.

Registered users reconnect through their identity. Guests reconnect through a participant token stored on the client.

Joining with the same identity from a new device invalidates the previous connection (single active session policy).

---

# 9. Module Responsibilities

## App

Spring Boot launcher.

Contains no business logic.

---

## Common

Shared utilities.

Exceptions.

Constants.

Utility classes.

Shared DTOs.

Domain event contract (DomainEvent) and event dispatcher.

---

## Identity

The identity bounded context (previously named Auth; renamed because authentication is just one thing an identity does).

Identity lifecycle (registered and guest).

Credentials (password hashes behind the PasswordHasher port).

User profiles (email is the login identifier).

Identity sessions (durable login sessions).

Roles, permissions, and the policy-based AuthorizationService (permissions are derived from roles in code, never persisted).

JWT infrastructure.

Guest Identity issuance.

CurrentUser port — the framework-independent request context all business services depend on.

---

## User

User-facing account features on top of the Identity module.

Preferences.

Quiz history.

(Profiles and roles live in the Identity module.)

---

## Quiz

Quiz management.

Questions.

Options.

Media references.

Validation.

---

## Session

Live quiz execution.

PIN generation.

Participants (backed by a Guest Identity or a Registered User).

Participant tokens.

Session recovery (reconnection, state restoration, connection rebinding).

Leaderboard.

Scoring.

---

## Media

Uploads.

Downloads.

Object storage integration.

---

## Security

Spring Security configuration.

JWT filters.

Authorization.

---

## WebSocket

Realtime communication.

STOMP configuration.

Subscribes to domain events and delivers them to clients.

Connections are ephemeral transport. Participant state never lives here.

Expected to generalize into a transport module (websocket, sse, mqtt) as delivery mechanisms grow.

---

# 10. Architectural Constraints

Modules communicate through public interfaces.

Modules never access another module's repositories directly.

Controllers never contain business logic.

Repositories never contain business logic.

Entities are persistence models.

DTOs are API contracts.

Domain state is durable. Transport state is ephemeral. The domain never depends on a transport (WebSocket, SSE, polling).

Only Application Services mutate aggregates, publish domain events, or open transactions. Everything else is read-only.

Domain events are framework independent.

---

# 11. Package Structure

Each module follows the same structure.

api

application

domain

infrastructure

Example

quiz

├── api

├── application

├── domain

└── infrastructure

---

# 12. Request Flow

HTTP Request

↓

Controller

↓

Application Service

↓

Domain

↓

Repository

↓

Database

Controllers orchestrate.

Domain models implement business rules.

Repositories persist.

---

# 13. Domain Events

State changes are announced through internal domain events.

HTTP / WebSocket

↓

Application Service

↓

Business Logic

↓

Publishes Domain Event

↓

Event Dispatcher

├── WebSocket Publisher

├── Audit Logger

└── Future subscribers (notifications, analytics)

Everything is reactive inside the application — without Kafka, RabbitMQ, or Spring Cloud. The dispatcher is in-process.

## The Event Model

Domain events are framework independent.

QuizChef defines its own contract instead of Spring's ApplicationEvent, so the domain never depends on the framework:

public interface DomainEvent {

    Instant occurredAt();

}

Examples:

QuestionStartedEvent

ParticipantJoinedEvent

ParticipantReconnectedEvent

AnswerSubmittedEvent

LeaderboardUpdatedEvent

SessionCompletedEvent

Events are pure domain concepts. The contract and the dispatcher live in the Common module.

## Rules

Only Application Services publish domain events.

Inbound commands always enter through an Application Service. The transport never orchestrates business logic.

Outbound realtime updates always leave through domain events. Domain modules never call the WebSocket module.

Subscribers perform delivery and side effects (publishing, logging). They never trigger business operations.

## What This Enables

Because every state change is an event, the session timeline can later be reconstructed — analytics, replay, moderation — without changing the domain.

This is not event sourcing. The database remains the source of truth. Events are notifications, not storage.

---

# 14. Dependency Rules

Allowed

Controller

↓

Application

↓

Domain

↓

Infrastructure

Forbidden

Controller → Repository

Repository → Controller

Infrastructure → API

Cross-module repository access

---

# 15. Technology Stack

Backend

Java 21

Spring Boot

Spring Security

Spring Data JPA

Flyway

PostgreSQL

Spring WebSocket

JJWT

Argon2 (spring-security-crypto)

MapStruct

Lombok

ArchUnit

Frontend

React

TypeScript

Vite

Tailwind

shadcn/ui

TanStack Query

Infrastructure

Docker Compose

GitHub Actions

MinIO

Railway

Cloudflare Pages

---

# 16. Data Storage

Relational data belongs in PostgreSQL.

Large files belong in Object Storage.

Application never stores images inside PostgreSQL.

---

# 17. Logging

Every request has

Correlation ID

User ID

Execution Time

Never log

Passwords

JWT

Secrets

Tokens

---

# 18. Error Handling

Single global exception handler.

Consistent API response format.

Never expose stack traces.

---

# 19. Security

JWT authentication.

Role-based authorization.

Input validation.

Output sanitization.

Rate limiting (future).

---

# 20. Scalability Strategy

Scale vertically first.

Scale horizontally later.

Split modules into services only when operational requirements justify it.

---

# 21. Quality Standards

Code should be:

Readable

Maintainable

Testable

Documented

Consistent

Performance is important.

Readability is mandatory.

---

# 22. Project Philosophy

Every engineer should be able to understand a feature within minutes.

Architecture exists to make development easier.

Avoid unnecessary abstractions.

Avoid premature optimization.

Prefer explicit code over clever code.

---

# 23. AI Contribution Rules

AI-generated code must follow this document.

AI must never:

Introduce new frameworks.

Replace architectural decisions.

Change module boundaries.

Generate placeholder implementations.

Leave TODO comments.

Every AI-generated change should be production quality.

---

# 24. Definition of Done

A feature is complete only if:

Code builds.

Tests pass.

Documentation updated.

API documented.

No warnings.

Reviewed.

Merged.

---

# 25. Future Evolution

Expected future modules:

Organization

Teams

Tournament

Analytics

Notifications

Question Bank

AI

Localization

Mobile

These additions must not require architectural redesign.

---

# 26. Guiding Principle

QuizChef is designed for the next contributor, not the current one.

Readable code is a feature.

Architecture should remove complexity, not introduce it.