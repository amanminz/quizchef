# Railway Deployment

This guide deploys QuizChef from the monorepo without changing application behavior. It uses the existing Spring Boot backend, React/Vite frontend, managed PostgreSQL, and S3-compatible object storage.

## Repository Overview

- `backend/`: Java 21 Spring Boot modular monolith. `backend/app` is the executable module.
- `frontend/`: React single-page app built by Vite and served by nginx.
- `docker/backend/Dockerfile`: production backend image.
- `docker/frontend/Dockerfile`: production frontend image.
- `railway.toml`: backend Railway config.
- `docker/frontend/railway.toml`: frontend Railway config.
- `docker-compose.prod.yml`: production-oriented Compose file for non-Railway hosts.

## Production Architecture

Run the backend and frontend as separate Railway services:

- Backend service: builds with `docker/backend/Dockerfile`, listens on `PORT` when Railway provides it, and exposes `/actuator/health/readiness`.
- Frontend service: builds static assets with `docker/frontend/Dockerfile`, serves nginx on `PORT` when Railway provides it, and exposes `/`.
- PostgreSQL: managed Railway PostgreSQL or another managed PostgreSQL service.
- Object storage: external S3-compatible storage such as Railway Object Storage, MinIO, or S3.

## Required Services

Create or connect these services before the first production deploy:

- PostgreSQL 16 compatible database.
- S3-compatible object storage bucket.
- Backend Railway service.
- Frontend Railway service.

Do not use the local `postgres`, `minio`, or `minio-init` services from `compose.yml` in production.

## Railway Setup

Create one Railway project with two services connected to the GitHub repository:

1. Backend service:
   - Source: GitHub repository.
   - Config file path: `/railway.toml`.
   - Root directory: repository root.
   - Dockerfile path comes from `railway.toml`.
   - Health check path: `/actuator/health/readiness`.
2. Frontend service:
   - Source: same GitHub repository.
   - Config file path: `/docker/frontend/railway.toml`.
   - Root directory: repository root.
   - The image defaults to `PORT=8080` and honors Railway's injected `PORT`.
   - Health check path: `/`.

Railway config-as-code applies build and deploy settings for each deployment; dashboard settings still hold service variables and domains.

## Environment Variables

Use `.env.production.example` as the source of truth. Configure real values in Railway service variables, not in Git.

Backend service variables:

| Variable | Required | Notes |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | Yes | Set to `prod`. |
| `DB_URL` | Yes | JDBC URL, for example `jdbc:postgresql://host:5432/db`. |
| `DB_USERNAME` | Yes | Production database user. |
| `DB_PASSWORD` | Yes | Production database password. |
| `JWT_SECRET` | Yes | At least 32 random characters; never use local/test placeholders. |
| `JWT_ACCESS_TOKEN_TTL` | No | Defaults to `PT15M`. |
| `JWT_AUDIENCE` | No | Optional audience claim. |
| `CORS_ALLOWED_ORIGINS` | Yes | Frontend origin, for example `https://quizchef.example.com`. |
| `MINIO_ENDPOINT` | Yes | S3-compatible endpoint. |
| `MINIO_ACCESS_KEY` | Yes | Object storage access key. |
| `MINIO_SECRET_KEY` | Yes | Object storage secret key. |
| `MINIO_BUCKET` | Yes | Existing media bucket name. |

Frontend service variables:

| Variable | Required | Notes |
| --- | --- | --- |
| `PORT` | No | Railway injects this automatically; the image defaults to `8080` for local/container Compose use. |
| `VITE_API_BASE_URL` | Depends | Use the backend public origin when backend and frontend are separate domains. Leave empty only for same-origin deployments. |
| `VITE_WS_URL` | Depends | Use `wss://<backend-domain>/ws/websocket` when frontend and backend are separate domains. Leave empty only for same-origin deployments. |

## Deploying Backend

1. Create or connect PostgreSQL.
2. Create or connect object storage and create the bucket named by `MINIO_BUCKET`.
3. Create the backend service from the repository.
4. Set config file path to `/railway.toml`.
5. Add all backend variables.
6. Deploy.
7. Confirm `/actuator/health/readiness` returns HTTP 200.

Flyway migrations run automatically at backend startup. A failed migration should fail the deployment health check instead of serving a partially started app.

Before a time-sensitive event, deploy only migrations that have already started successfully in local or staging. This PR does not add or modify any Flyway migration files.

## Deploying Frontend

1. Create the frontend service from the same repository.
2. Set config file path to `/docker/frontend/railway.toml`.
3. Set `VITE_API_BASE_URL` and `VITE_WS_URL` for the backend public domain.
4. Deploy.
5. Open the frontend domain and verify static assets load.

Vite environment variables are build-time variables. Changing `VITE_API_BASE_URL` or `VITE_WS_URL` requires a frontend redeploy.

## Custom Domain

Use separate domains unless a later PR introduces a reverse proxy:

- Frontend: `quizchef.example.com`.
- Backend: `api.quizchef.example.com`.

After adding domains in Railway, update:

- Backend `CORS_ALLOWED_ORIGINS=https://quizchef.example.com`.
- Frontend `VITE_API_BASE_URL=https://api.quizchef.example.com`.
- Frontend `VITE_WS_URL=wss://api.quizchef.example.com/ws/websocket`.

## HTTPS

Railway terminates HTTPS for Railway-managed and custom domains. Use `https://` for REST URLs and `wss://` for WebSocket URLs in frontend build variables.

## GoDaddy DNS

For a GoDaddy-managed domain:

1. Add the custom domain in the target Railway service.
2. Copy the DNS target Railway provides.
3. In GoDaddy DNS, create or update the record Railway requests.
4. Use `CNAME` records for subdomains such as `quizchef` and `api` when Railway asks for CNAME.
5. Wait for DNS propagation and Railway certificate issuance.
6. Verify both HTTPS domains before announcing the URL.

Keep DNS changes narrow. Do not replace nameservers unless the whole domain is intentionally moving to another DNS provider.

## Rollback

Use Railway deployment history:

1. Open the failed service.
2. Select the previous successful deployment.
3. Redeploy or promote that deployment.
4. Confirm the health check passes.
5. If only frontend build variables changed, redeploy the previous frontend deployment.

If a backend deploy ran a database migration, confirm rollback compatibility before promoting an older backend image.

## Health Checks

Backend production health endpoint:

```bash
curl -i https://api.quizchef.example.com/actuator/health/readiness
```

Expected response: HTTP 200 with readiness status `UP`. Production hides health details.

Frontend health endpoint:

```bash
curl -i https://quizchef.example.com/
```

Expected response: HTTP 200.

The backend readiness endpoint is public by design so Railway and other orchestrators can check readiness without a JWT.

## Troubleshooting

- Backend fails immediately: confirm `SPRING_PROFILES_ACTIVE=prod`, required variables are present, and `JWT_SECRET` is not a checked-in placeholder.
- Backend health check times out: check PostgreSQL reachability, `DB_URL`, credentials, and Flyway migration logs.
- Login or API calls fail from the browser: confirm `CORS_ALLOWED_ORIGINS` exactly matches the frontend origin.
- Frontend calls the wrong backend: update `VITE_API_BASE_URL` and redeploy the frontend.
- Realtime connection fails: confirm `VITE_WS_URL` uses `wss://` and ends with `/ws/websocket`.
- Object upload fails: confirm `MINIO_ENDPOINT`, access key, secret key, and bucket name.
- Custom domain is pending: verify DNS record type/value in GoDaddy and wait for certificate issuance.

## Production Compose

For non-Railway hosts, use:

```bash
docker compose --env-file .env.production -f docker-compose.prod.yml up -d --build
```

This file starts only the backend and frontend. PostgreSQL and object storage must already exist and be reachable from the host.
