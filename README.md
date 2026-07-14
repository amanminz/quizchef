# QuizChef

QuizChef is an open-source, self-hostable platform for creating, hosting, and playing live quizzes.

## Repository layout

```text
backend/   Java 21 Spring Boot modular monolith
frontend/  React single-page application
docs/      Product, architecture, and engineering documentation
docker/    Reserved for future container infrastructure
scripts/   Reserved for project automation
```

The backend is organized by feature. `app` is the only executable module; feature modules expose `api`, `application`, `domain`, and `infrastructure` layers as functionality is introduced.

## Prerequisites

- Java 21
- Node.js 20 or later

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