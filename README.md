# QuizChef

QuizChef is an open-source, self-hostable platform for creating, hosting, and playing live quizzes.

## Repository layout

```text
backend/   Java 21 Spring Boot modular monolith
frontend/  React single-page application
docker/    Dockerfiles and container configuration
docs/      Product, architecture, and engineering documentation
scripts/   Reserved for project automation
```

The backend is organized by feature. `app` is the only executable module; feature modules expose `api`, `application`, `domain`, and `infrastructure` layers as functionality is introduced.

## Quick start with Docker

The only prerequisite is Docker with the Compose plugin.

```bash
git clone https://github.com/amanminz/quizchef.git
cd quizchef
docker compose up
```

This builds and starts the complete development environment:

| Service       | URL                                          | Notes                             |
| ------------- | -------------------------------------------- | --------------------------------- |
| Frontend      | http://localhost:3000                        | React app served by nginx         |
| Backend       | http://localhost:8080                        | Spring Boot API                   |
| Swagger UI    | http://localhost:8080/swagger-ui/index.html  | API documentation                 |
| OpenAPI JSON  | http://localhost:8080/v3/api-docs            | Machine-readable API spec         |
| Health        | http://localhost:8080/actuator/health        | Application readiness             |
| PostgreSQL    | localhost:5432                               | Database `quizchef`               |
| MinIO API     | http://localhost:9000                        | Object storage                    |
| MinIO Console | http://localhost:9001                        | Login `quizchef` / `quizchef-local` |

Database migrations run automatically at backend startup via Flyway. The `quizchef-media` bucket is created automatically in MinIO.

The stack is defined in `compose.yml` (environment-agnostic services and health checks) plus `compose.override.yml` (local development concerns such as host port publishing), which Docker Compose merges automatically.

Stop the environment with `docker compose down`. Add `-v` to also delete the PostgreSQL and MinIO volumes.

## Environment variables

All variables have working local defaults, so no configuration is required for a first run. To customize, copy the template and edit it:

```bash
cp .env.example .env
```

| Variable             | Default          | Purpose                          |
| -------------------- | ---------------- | -------------------------------- |
| `POSTGRES_DB`        | `quizchef`       | Database name                    |
| `POSTGRES_USER`      | `quizchef`       | Database user                    |
| `POSTGRES_PASSWORD`  | `quizchef`       | Database password                |
| `POSTGRES_PORT`      | `5432`           | Host port for PostgreSQL         |
| `MINIO_ROOT_USER`    | `quizchef`       | MinIO root user / access key     |
| `MINIO_ROOT_PASSWORD`| `quizchef-local` | MinIO root password / secret key |
| `MINIO_BUCKET`       | `quizchef-media` | Media bucket name                |
| `MINIO_PORT`         | `9000`           | Host port for the MinIO API      |
| `MINIO_CONSOLE_PORT` | `9001`           | Host port for the MinIO console  |
| `BACKEND_PORT`       | `8080`           | Host port for the backend        |
| `FRONTEND_PORT`      | `3000`           | Host port for the frontend       |
| `JWT_SECRET`         | dev-only default | JWT signing secret (min. 32 chars) |
| `JWT_AUDIENCE`       | unset            | Optional JWT audience claim, asserted when set |

The backend itself is configured through Spring profiles (`local`, `dev`, `test`, `prod`) in `backend/app/src/main/resources/`. Outside Docker it reads `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `MINIO_ENDPOINT`, `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`, `MINIO_BUCKET`, `JWT_SECRET`, and `JWT_ACCESS_TOKEN_TTL`; the `local` profile provides defaults matching the Compose stack. The `dev` and `prod` profiles require these variables to be set — no secrets are committed.

Running the backend tests requires Docker: the integration tests start a disposable PostgreSQL via Testcontainers.

## Local development without Docker

Prerequisites: Java 21 and Node.js 20 or later.

Start only the infrastructure services in Docker:

```bash
docker compose up postgres minio minio-init
```

Then run the backend and frontend natively:

```bash
./gradlew -p backend :app:bootRun
```

```bash
cd frontend
npm ci
npm run dev
```

The backend defaults to the `local` profile, which points at `localhost:5432` and `localhost:9000`.

## Dev container

The repository includes a [Dev Container](https://containers.dev) configuration in `.devcontainer/`. Open the folder in VS Code (or GitHub Codespaces) and choose **Reopen in Container** to get a ready-made environment with Java 21, Node.js 20, Gradle, and Docker.

## Build

```bash
./gradlew -p backend build
cd frontend && npm ci && npm run build
```

On Windows, use `gradlew.bat` for the backend command.

## Documentation

Read the project documentation before contributing:

1. `docs/product/PRODUCT_REQUIREMENTS.md`
2. `docs/architecture/ARCHITECTURE.md`
3. `docs/development/CODING_STANDARDS.md`
4. `docs/development/AI_GUIDELINES.md`
