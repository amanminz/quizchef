# QuizChef Frontend

React 19 + TypeScript + Vite. Architecture: [RFC-009](../docs/rfcs/RFC-009-frontend-architecture.md).

## Development

```bash
npm install
npm run dev        # http://localhost:5173 — proxies /api and /ws to localhost:8080
```

Run the backend alongside (`./gradlew -p backend :app:bootRun` with the local infrastructure up). No `.env` needed in development; see `.env.example` for overrides.

## Scripts

| Script                                    | Purpose                                                         |
| ----------------------------------------- | --------------------------------------------------------------- |
| `npm run dev`                             | Dev server with API/WS proxy                                    |
| `npm test` / `npm run test:watch`         | Vitest + Testing Library + MSW                                  |
| `npm run lint` / `npm run typecheck`      | ESLint / `tsc -b`                                               |
| `npm run format` / `npm run format:check` | Prettier                                                        |
| `npm run build`                           | Typecheck + production build                                    |
| `npm run generate:api`                    | Regenerate `src/types/api.gen.ts` from the backend OpenAPI spec |

## Regenerating API types

Request/response models are generated, never hand-written:

```bash
cd .. && ./gradlew -p backend :app:test --tests "io.quizchef.app.OpenApiSpecExportTest"
cd frontend && npm run generate:api
```

The first command exports the live spec to `backend/app/build/openapi.json`; the second turns it into TypeScript. `api.gen.ts` is committed so the frontend builds without the backend present.
