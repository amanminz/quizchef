#!/usr/bin/env bash
#
# Guards the one thing about the local Docker stack that is easy to break and
# hard to notice: the browser must be able to reach the API.
#
# The failure is quiet and misleading. nginx serves the SPA and has no /api
# route of its own, so an API call that reaches it lands on the SPA fallback
# and a POST comes back 405 Not Allowed — which reads as a broken backend but
# never reached one. `npm run dev` hides this entirely, because the Vite dev
# server proxies /api and /ws itself; only the containerized stack is affected.
#
# The local stack solves it by proxying, so the browser and the API share an
# origin. That also sidesteps CORS, which is the second trap here:
# http://localhost:3000, http://127.0.0.1:3000, a WSL address, and the LAN
# address a phone would use are four different origins, and an allowlist can
# only name so many.
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

# The proxy is mounted where the server block's include picks it up.
mounted="$(printf '%s' "$local_config" | python3 -c '
import json, sys
volumes = json.load(sys.stdin)["services"]["frontend"].get("volumes") or []
print(next((v["target"] for v in volumes
            if v["target"].startswith("/etc/nginx/site-extra/")
            and v["target"].endswith(".conf")), ""))')"
[ -n "$mounted" ] ||
  fail "no API proxy is mounted into /etc/nginx/site-extra — API calls would hit the SPA fallback and POSTs would 405"
pass "API proxy mounted at $mounted"

grep -q 'include /etc/nginx/site-extra/\*\.conf;' docker/frontend/nginx.conf ||
  fail "docker/frontend/nginx.conf no longer includes /etc/nginx/site-extra/*.conf, so the mount above is never read"
pass "the server block includes it"

for path in "location /api/" "location /ws"; do
  grep -q "^$path" docker/frontend/local-api-proxy.conf ||
    fail "local-api-proxy.conf has no '$path' block"
done
pass "it proxies both /api and /ws"

# $host drops the port. The backend would then reconstruct a different origin
# than the browser sent, treat a same-origin POST as cross-origin, and 403
# every hostname but the one the CORS allowlist happens to name.
! grep -qE '^[[:space:]]*proxy_set_header Host \$host;' docker/frontend/local-api-proxy.conf ||
  fail "proxy_set_header Host uses \$host, which drops the port — use \$http_host"
grep -qE '^[[:space:]]*proxy_set_header Host \$http_host;' docker/frontend/local-api-proxy.conf ||
  fail "proxy_set_header Host \$http_host is missing — the backend needs the port to see the request as same-origin"
pass "it forwards Host with the port intact (\$http_host)"

# Same-origin means the bundle must NOT be built pointing somewhere else.
api_base_url="$(printf '%s' "$local_config" | python3 -c '
import json, sys
build = json.load(sys.stdin)["services"]["frontend"].get("build") or {}
print((build.get("args") or {}).get("VITE_API_BASE_URL") or "")')"
[ -z "$api_base_url" ] ||
  fail "VITE_API_BASE_URL is set to '$api_base_url' — with the proxy in place the bundle should stay same-origin, or every browser will call that one host regardless of how the page was opened"
pass "the bundle stays same-origin (VITE_API_BASE_URL unset)"

# The proxy is local-only by construction: compose.override.yml is merged only
# with compose.yml, never with the production file, which is passed explicitly
# with -f. Read as text because docker-compose.prod.yml deliberately has no
# defaults for its secrets (RFC-011), so it cannot be rendered without
# inventing a dozen placeholder credentials.
echo "Production stack (docker-compose.prod.yml)"
if grep -q "site-extra" docker-compose.prod.yml; then
  fail "the local API proxy has leaked into docker-compose.prod.yml — the two are separate Railway services with no shared network, and 'backend' would not resolve"
fi
pass "no proxy mount (the image ships the include directory empty)"

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
