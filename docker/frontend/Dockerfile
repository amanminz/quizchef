# syntax=docker/dockerfile:1

# Build context is the repository root so the nginx configuration in
# docker/frontend/ can be copied alongside the frontend sources.

# ---------- Build stage ----------
FROM node:20-alpine AS build
WORKDIR /workspace

# Copy the lockfile first so npm ci is cached until dependencies change.
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

COPY frontend/ ./
RUN npm run build

# ---------- Runtime stage ----------
# nginx-unprivileged runs as a non-root user and listens on port 8080.
FROM nginxinc/nginx-unprivileged:1.27-alpine
COPY docker/frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist/ /usr/share/nginx/html/

EXPOSE 8080
