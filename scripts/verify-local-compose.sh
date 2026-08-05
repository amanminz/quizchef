#!/usr/bin/env bash
#
# Guards the one thing about the local Docker stack that is easy to break and
# hard to notice: the frontend image must be built knowing where the backend
# is.
#
# Vite inlines VITE_* at build time, so an image built without
# VITE_API_BASE_URL falls back to same-origin — and every API call then goes
# to the nginx serving the static files, which has no /api route. Requests
# never reach the backend at all; POSTs come back 405 Not Allowed, which
# looks like a broken API and isn't. `npm run dev` hides this completely,
# because the Vite dev server proxies /api and /ws itself.
#
# Checks the *rendered* config rather than the YAML text, so it follows
# variable substitution and file merging the way Docker actually does.
#
# Usage: scripts/verify-local-compose.sh
set -euo pipefail

cd "$(dirname "$0")/.."

fail() { printf '  ✗ %s\n' "$1" >&2; exit 1; }
pass() { printf '  ✓ %s\n' "$1"; }

command -v docker >/dev/null || fail "docker is not installed"
docker compose version >/dev/null 2>&1 || fail "the docker compose plugin is not available"

echo "Local stack (compose.yml + compose.override.yml)"

local_config="$(docker compose config --format json)"

api_base_url="$(printf '%s' "$local_config" |
  python3 -c 'import json,sys; print(json.load(sys.stdin)["services"]["frontend"]["build"]["args"].get("VITE_API_BASE_URL") or "")')"
[ -n "$api_base_url" ] ||
  fail "frontend build arg VITE_API_BASE_URL is empty — the bundle would fall back to same-origin and every API call would 405 against nginx"
pass "frontend builds with VITE_API_BASE_URL=$api_base_url"

# The URL is only correct if it names the port the backend is actually
# published on: these two live in different files and drift silently.
backend_port="$(printf '%s' "$local_config" |
  python3 -c 'import json,sys; print(json.load(sys.stdin)["services"]["backend"]["ports"][0]["published"])')"
case "$api_base_url" in
  *":$backend_port") pass "it points at the published backend port ($backend_port)" ;;
  *) fail "VITE_API_BASE_URL ($api_base_url) does not match the published backend port ($backend_port)" ;;
esac

# Split origins mean the browser's requests are cross-origin, so the backend
# has to allow the frontend's origin or every call fails preflight instead.
frontend_port="$(printf '%s' "$local_config" |
  python3 -c 'import json,sys; print(json.load(sys.stdin)["services"]["frontend"]["ports"][0]["published"])')"
cors="$(printf '%s' "$local_config" |
  python3 -c 'import json,sys; print(json.load(sys.stdin)["services"]["backend"]["environment"].get("CORS_ALLOWED_ORIGINS") or "")')"
case "$cors" in
  *"localhost:$frontend_port"*) pass "backend CORS allows http://localhost:$frontend_port" ;;
  *) fail "CORS_ALLOWED_ORIGINS ($cors) does not cover the frontend origin (http://localhost:$frontend_port)" ;;
esac

# The override is local-only by construction: compose.override.yml is merged
# only with compose.yml, never with the production file, which is passed
# explicitly with -f. This asserts the consequence rather than the mechanism
# — production must source its own URL from the environment, with no
# localhost anywhere near it.
#
# Read as text rather than rendered: docker-compose.prod.yml deliberately has
# no defaults for its secrets (RFC-011 — a missing value should fail startup,
# not deploy something insecure), so it cannot be rendered without inventing
# a dozen placeholder credentials, and the invariant here is about what the
# file declares anyway.
echo "Production stack (docker-compose.prod.yml)"
prod_arg="$(python3 - <<'PYTHON'
import re, sys
text = open("docker-compose.prod.yml", encoding="utf-8").read()
match = re.search(r"^\s*VITE_API_BASE_URL:\s*(.+)$", text, re.MULTILINE)
sys.exit("declares no VITE_API_BASE_URL build arg") if match is None else print(match.group(1).strip())
PYTHON
)" || fail "docker-compose.prod.yml $prod_arg"

case "$prod_arg" in
  *localhost*) fail "production declares $prod_arg — a local URL has leaked into it" ;;
  '${VITE_API_BASE_URL'*) pass "sources its own URL from the environment ($prod_arg)" ;;
  *) fail "production's VITE_API_BASE_URL ($prod_arg) is not environment-supplied" ;;
esac

echo "Local Docker routing contract holds."
