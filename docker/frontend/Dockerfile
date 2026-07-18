# syntax=docker/dockerfile:1

# Build context is the repository root so the nginx configuration in
# docker/frontend/ can be copied alongside the frontend sources.

# ---------- Build stage ----------
FROM node:20-alpine AS build
WORKDIR /workspace
ARG VITE_API_BASE_URL=
ARG VITE_WS_URL=
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}
ENV VITE_WS_URL=${VITE_WS_URL}

# Copy the lockfile first so npm ci is cached until dependencies change.
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

COPY frontend/ ./
RUN npm run build

# ---------- Runtime stage ----------
# nginx-unprivileged runs as a non-root user. Its entrypoint renders the
# template with PORT, defaulting to 8080 locally and honoring Railway's PORT.
FROM nginxinc/nginx-unprivileged:1.27-alpine
ENV PORT=8080
COPY docker/frontend/nginx.conf /etc/nginx/templates/default.conf.template
COPY --from=build /workspace/dist/ /usr/share/nginx/html/

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 CMD wget -q --spider "http://127.0.0.1:${PORT}/" || exit 1
