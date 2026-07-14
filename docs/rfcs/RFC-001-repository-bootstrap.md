# RFC-001 Repository Foundation

Status

Accepted

Authors

Aman Minz

Created

2026-07-14

Updated

2026-07-14

---

# Summary

This RFC records the foundational decisions made while bootstrapping the QuizChef repository and its local infrastructure: the build system, the module layout, containerization, database migrations, object storage, configuration profiles, orchestration, and the security baseline. It is the reference for every future contributor who asks "why is the repository shaped this way?"

---

# Motivation

The repository bootstrap (PR #1) and local infrastructure (PR #2) introduced architectural decisions that are not visible from the code alone. Documenting them prevents future PRs from re-litigating settled questions and gives new contributors the reasoning, not just the result.

---

# Goals

- Record why each foundational technology was chosen.
- Define the repository layout and its boundaries.
- Describe how a contributor runs the platform locally.

---

# Non Goals

- Feature design (authentication, quiz engine, sessions) — covered by RFC-002 through RFC-007.
- Production deployment — covered by RFC-008.

---

# Background

QuizChef is a self-hostable live quiz platform (see `docs/architecture/ARCHITECTURE.md`). The architecture is a Spring Boot modular monolith with a React frontend, PostgreSQL, and MinIO. The repository must let a new contributor go from `git clone` to a running environment with one command.

---

# Proposed Design

## Repository layout

```text
backend/   Java 21 Spring Boot modular monolith (Gradle multi-module)
frontend/  React + TypeScript + Vite single-page application
docker/    Dockerfiles and container configuration
docs/      Product, architecture, and engineering documentation
scripts/   Reserved for project automation
```

Compose files (`compose.yml`, `compose.override.yml`) live at the repository root so `docker compose up` works from a fresh clone without flags.

## Why Gradle?

- First-class multi-module support, which the modular monolith requires.
- The Kotlin DSL gives type-checked, IDE-assisted build scripts.
- The wrapper (`gradlew`) pins the exact build tool version for every contributor and for CI, eliminating environment drift.
- Better incremental build performance than Maven for multi-module projects.

## Why multi-module?

The architecture mandates a modular monolith: one deployable, many modules (`app`, `common`, `auth`, `user`, `quiz`, `session`, `media`, `security`, `websocket`). Gradle modules turn the architectural boundary into a compile-time boundary: a module can only use what it explicitly declares a dependency on, so accidental coupling fails the build instead of surfacing in review. `app` is the only executable module and contains no business logic. ArchUnit is on the test classpath to enforce intra-module layering (`api` → `application` → `domain` → `infrastructure`) starting with the authentication milestone.

## Why Docker?

- Contributors need only Docker — no local Java, Node, PostgreSQL, or MinIO installation.
- The images are production-quality from day one: multi-stage builds, small runtime images (alpine JRE, unprivileged nginx), non-root users, and layer-cache-friendly ordering (build scripts and lockfiles copied before sources).
- The backend image extracts the Spring Boot layered jar so unchanged dependency layers are reused across rebuilds.

## Why Compose?

- One command (`docker compose up`) starts the complete environment with correct ordering: services declare health checks, and dependents wait on `condition: service_healthy`.
- Compose V2 file naming is used. `compose.yml` holds the environment-agnostic definition; `compose.override.yml` (merged automatically) holds local-development concerns such as host port publishing. Additional environment files (for example a production variant) can be layered the same way when RFC-008 lands.
- Named volumes (`postgres-data`, `minio-data`) persist data across restarts; a dedicated bridge network isolates the stack.

## Why PostgreSQL?

- Relational integrity fits the domain (quizzes, questions, sessions, participants, scores).
- Open source, self-hostable, and available on every deployment target.
- Created with UTF-8 encoding so any language can appear in quiz content.

## Why Flyway?

- Schema is code: every change is a versioned SQL migration, reviewed like any other change.
- Flyway runs at application startup and a failed migration aborts startup, so an application version can never run against a schema it does not understand.
- Hibernate auto-DDL is forbidden (`ddl-auto: validate`); the database schema has exactly one owner.
- `V1__init.sql` creates only the `quizchef` schema. Tables arrive with the features that need them.

## Why MinIO?

- Media (images, audio, video) does not belong in PostgreSQL; the architecture mandates object storage.
- MinIO is S3-compatible, open source, and self-hostable — the same client code will later work against any S3-compatible provider (AWS S3, Cloudflare R2).
- The media module will consume storage through its own abstraction, keeping the provider swappable.
- The `quizchef-media` bucket is created idempotently at startup by a one-shot `mc` container.

## Why Spring profiles?

- `application.yml` holds shared defaults; `local`, `dev`, `test`, and `prod` hold environment overrides.
- `local` is the default profile and carries working defaults that match the Compose stack, so a fresh clone runs with zero configuration.
- `dev` and `prod` contain no credentials — all secrets arrive through environment variables. Nothing secret is committed.
- `prod` disables Swagger UI and detailed health output.

## Why a security baseline?

Spring Security is on the classpath from the start (the architecture requires JWT authentication later). Without configuration it would lock every endpoint behind a generated password; without Spring Security there would be a window where endpoints ship unprotected. The baseline in the `security` module is deny-by-default: only the patterns in `PublicEndpoints` (health, info, API docs) are reachable, and every future endpoint must be authorized explicitly by the module that introduces it. Sessions are stateless in preparation for JWT.

## Why GitHub Actions CI?

- CI lives next to the code (`.github/workflows/ci.yml`) and runs on every push and pull request to `main`: backend build and tests, frontend build.
- GitHub-hosted runners keep the pipeline free for an open-source project and require no self-managed infrastructure.

## Why a dev container?

`.devcontainer/` provisions Java 21, Node 20, Gradle, and Docker-in-Docker for VS Code and GitHub Codespaces, so "works on my machine" disappears for contributors who prefer a managed environment. It is optional; native tooling and plain Docker remain fully supported.

---

# Alternatives Considered

**Maven instead of Gradle** — rejected: weaker multi-module ergonomics, slower incremental builds, XML configuration.

**Single backend module** — rejected: module boundaries would exist only by convention and erode; the architecture explicitly requires enforceable boundaries.

**Microservices** — rejected by the architecture document: higher operational cost and complexity with no benefit at this scale.

**Hibernate auto-DDL instead of Flyway** — rejected: non-deterministic schema evolution, no review trail, forbidden by the coding standards.

**Storing media in PostgreSQL** — rejected: bloats the database, poor streaming performance, forbidden by the architecture.

**Local filesystem storage instead of MinIO** — rejected as the primary mechanism: not S3-compatible, does not survive horizontal scaling, and would diverge local behavior from production.

**Postponing Spring Security until authentication** — rejected: retrofitting security is riskier than starting deny-by-default.

---

# Risks

- The backend Docker build resolves dependencies inside the image; cold builds are slow on machines without cached layers. Mitigated by layer-cache-friendly Dockerfile ordering.
- Compose health checks depend on tools inside the images (`pg_isready`, `mc`, busybox `wget`); base-image changes could silently break them. Mitigated by validating `docker compose up` in every infrastructure PR.
- Local default credentials (`quizchef` / `quizchef-local`) must never be reused outside local development. `dev` and `prod` profiles refuse to start without externally provided credentials.

---

# Migration

Not applicable — this RFC documents the initial state.

---

# Open Questions

- Which registry will host released images (decided in RFC-008)?
- Whether a `compose.prod.yml` layer or a separate deployment mechanism serves production (decided in RFC-008).

---

# Acceptance Criteria

- [x] `./gradlew -p backend build` passes on a fresh clone.
- [x] `npm run build` passes in `frontend/`.
- [x] `docker compose up` starts PostgreSQL, MinIO, backend, and frontend, all healthy.
- [x] Flyway applies `V1__init.sql` at backend startup.
- [x] Swagger UI, OpenAPI JSON, and actuator health/info respond; everything else returns 403.
- [x] CI builds backend and frontend on every push and pull request.

---

# Future Work

- ArchUnit rules enforcing module layering (from the authentication milestone onwards).
- Storage abstraction in the media module (`StorageService` with an S3-compatible implementation).
- Production Compose layer or deployment pipeline (RFC-008).
