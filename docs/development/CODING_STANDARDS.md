# QuizChef Coding Standards

**Version:** 1.0

**Status:** Living Document

---

# 1. Purpose

This document defines the coding standards for QuizChef.

Every contributor (human or AI) MUST follow these guidelines.

The goals are:

- Consistency
- Readability
- Maintainability
- Testability
- Production-quality code

---

# 2. General Principles

Code should optimize for readability rather than cleverness.

Every engineer should be able to understand a feature in under 10 minutes.

If a solution requires extensive comments to explain, it should be simplified.

---

# 3. Java Version

Use Java 21.

Always prefer modern Java features when they improve readability.

Examples:

- Records
- Sealed interfaces (when appropriate)
- Pattern matching
- Switch expressions
- Text blocks

Do not use preview features.

---

# 4. Package Naming

All packages are lowercase.

Example

io.quizchef.quiz.application

Never use

controller

service

repository

at the root package.

Packages must follow Feature First architecture.

Correct

io.quizchef.quiz

io.quizchef.user

io.quizchef.session

Incorrect

io.quizchef.controller

io.quizchef.service

---

# 5. Module Structure

Every feature module follows exactly the same structure.

quiz

├── api
├── application
├── domain
└── infrastructure

Definitions

api

REST controllers

Request DTOs

Response DTOs

application

Use cases

Application services

Commands

Queries

domain

Entities

Value Objects

Business Rules

Domain Services

Enums

infrastructure

Persistence

JPA

Repositories

Configuration

Adapters

---

# 6. Naming Conventions

Classes

PascalCase

Interfaces

PascalCase

Enums

PascalCase

Methods

camelCase

Variables

camelCase

Constants

UPPER_SNAKE_CASE

Packages

lowercase

---

# 7. Class Naming

Controllers

QuizController

SessionController

Services

QuizApplicationService

SessionApplicationService

Repositories

QuizRepository

ParticipantRepository

Entities

Quiz

Question

Option

DTOs

CreateQuizRequest

QuizResponse

Commands

CreateQuizCommand

Queries

FindQuizQuery

Events

QuizStartedEvent

---

# 8. Dependency Injection

Use constructor injection only.

Never use field injection.

Never use

@Autowired

on fields.

---

# 9. Lombok

Allowed

@Getter

@Setter (only where appropriate)

@Builder

@RequiredArgsConstructor

@NoArgsConstructor

@AllArgsConstructor

Forbidden

@Data

Reason

@Data generates equals(), hashCode() and toString() that are often incorrect for JPA entities.

---

# 10. DTOs

Prefer Java Records.

Example

public record CreateQuizRequest(
    String title,
    String description
) {}

DTOs should never contain business logic.

---

# 11. Entities

Entities represent persistence.

Entities are not API models.

Entities should not be returned directly from controllers.

Every entity should contain

id

createdAt

updatedAt

Use UUID as primary keys.

---

# 12. Controllers

Controllers should

Validate input

Call application service

Return response

Controllers should never

Contain business logic

Access repositories

Perform calculations

---

# 13. Application Services

Responsible for

Orchestration

Transactions

Calling domain objects

Application services should not contain persistence logic.

---

# 14. Domain Layer

Business rules belong here.

Examples

Scoring

PIN generation

Leaderboard calculations

Validation rules

The domain should be framework independent whenever practical.

---

# 15. Repository Layer

Repositories only perform persistence.

Repositories should never

Contain business logic

Call other repositories

Return DTOs

---

# 16. Validation

Use Jakarta Validation.

Validate at the API boundary.

Example

@NotBlank

@Email

@NotNull

@Positive

Never manually validate request DTOs.

---

# 17. Exception Handling

Use a global exception handler.

Create custom exceptions.

Example

QuizNotFoundException

SessionClosedException

ParticipantAlreadyJoinedException

Never throw generic RuntimeException.

---

# 18. Logging

Use SLF4J.

Log meaningful events.

Never log

Passwords

JWT

Secrets

PII beyond what is operationally necessary

Prefer parameterized logging.

Correct

log.info("Quiz {} started", quizId);

Incorrect

log.info("Quiz started " + quizId);

---

# 19. Transactions

Use @Transactional only in Application Services.

Never in controllers.

Never in repositories.

---

# 20. API Design

REST conventions

GET

POST

PUT

PATCH

DELETE

Plural resource names.

Example

/api/v1/quizzes

/api/v1/sessions

Every public endpoint must include the version segment.

/api/v1/...

No exceptions.

Never expose entities directly.

---

# 21. JSON

Use camelCase.

Dates should use ISO-8601.

Always return UTC timestamps.

---

# 22. Database

Use Flyway only.

Never use ddl-auto=create.

Never modify production schema manually.

All schema changes must be versioned.

---

# 23. Testing

Testing Pyramid

Unit Tests

Integration Tests

End-to-End Tests

Use

JUnit 5

Mockito

Testcontainers

Test names should describe behavior.

Example

shouldCreateQuizSuccessfully()

Avoid testing implementation details.

---

# 24. Documentation

Every public class should have a clear responsibility.

Public APIs must be documented using OpenAPI annotations where needed.

Complex business logic should include concise comments explaining the "why", not the "what".

---

# 25. Git

Branch naming

feature/quiz-crud

feature/auth-login

bugfix/session-timeout

Commit messages

feat: add quiz creation

fix: resolve session join issue

docs: update architecture

refactor: simplify scoring strategy

test: add integration tests

Use Conventional Commits.

---

# 26. Pull Requests

Every PR should

Compile

Pass tests

Update documentation if required

Contain a meaningful description

Avoid unrelated changes

---

# 27. Code Reviews

Review for

Correctness

Readability

Security

Performance

Test coverage

Architecture compliance

Not personal coding style.

---

# 28. AI Contribution Rules

AI-generated code must

Compile

Follow architecture

Follow naming standards

Follow module boundaries

Include tests where appropriate

AI must never

Introduce new frameworks

Ignore architecture

Leave TODOs

Generate placeholder methods

Commit commented-out code

---

# 29. Performance

Optimize only after measuring.

Prefer clarity over micro-optimizations.

Avoid premature optimization.

---

# 30. Security

Never trust client input.

Validate every request.

Escape user-generated output where applicable.

Use least-privilege access.

Keep secrets outside source code.

---

# 31. Definition of Done

A feature is complete only when

✓ Code builds

✓ Tests pass

✓ Documentation updated

✓ API documented

✓ Flyway migration added (if required)

✓ Code reviewed

✓ CI passes

✓ No TODOs

✓ No compiler warnings

---

# 32. Philosophy

Write code for the next engineer.

The best code is obvious.

If something feels clever, simplify it.

Consistency beats perfection.
