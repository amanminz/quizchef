# Deployment Checklist

The steps of a deploy itself, as the system exists today: CI (`.github/workflows/ci.yml`) builds and tests on every push/PR to `main` but does **not** deploy automatically — deployment automation is RFC-008 (Draft, unbuilt). This checklist is the manual sequence until that lands.

---

## 1. Before deploying

- [ ] CI is green on the commit being deployed (`./gradlew -p backend build test`, `npm run build`, `npm test` in `frontend/` — the same steps CI runs).
- [ ] Work through the [Production Checklist](production-checklist.md).
- [ ] Any new Flyway migration has been reviewed for backward compatibility with the currently-running version, if this is a rolling deploy (this project runs one instance today, so this is a lighter concern than at scale — still worth a look for destructive migrations).
- [ ] `CORS_ALLOWED_ORIGINS` and `JWT_SECRET` are set for the target environment — neither has a production default (RFC-011), so a missing value fails startup rather than deploying insecurely.
- [ ] `server.forward-headers-strategy: framework` (`application-prod.yml`) is deliberately `prod`-only — it makes `ClientIpResolver` (RFC-011) trust `X-Forwarded-For`, which is only safe behind a reverse proxy that sets it correctly (Railway). Never enable this outside an environment with such a proxy in front, or IP-based rate limits become spoofable.

## 1b. When the two services can disagree

The backend and frontend are separate Railway services built from the same
push, and they do not finish at the same moment. For most releases that
window is invisible. It is not invisible when a release changes a shared
contract, and RFC-019 is the current example: it removes `rank` from the
participant progress read, deletes the `rank-context` route, and adds
`/final-placement`.

The three combinations, and what each actually does:

| Combination | Behaviour |
| --- | --- |
| New frontend → new backend | Correct. |
| New frontend → old backend | Degrades quietly. `/final-placement` returns `http.404`, which the client reads as "results aren't out yet" and shows the announcement-waiting screen rather than an error (`isEndpointMissing`); a missing `pointsEarned` omits that line; a `rank` still sent by the old backend is never read. Covered by tests. |
| Old frontend → new backend | **Cosmetically broken.** The old bundle renders `rankOrdinal(result.rank ?? 0)`, so a participant sees a 6xl **"0th"** where their rank used to be. Ranking neighbours silently disappear (the old client tolerates the 404). An old *host* bundle would also render the full standings below the podium, since it predates the reveal cutoff. |

That last row cannot be fixed from the new code — the broken client is
already shipped, and the only server-side "fix" would be to keep sending
the rank this release exists to remove. So:

- [ ] **Deploy when no session is live.** This is already the standing rule
      during events (feature freeze), and it is what makes the window
      harmless: no participant is on a results screen to see "0th", and no
      host is mid-ceremony.
- [ ] After deploying, confirm **both** Railway services report the new
      build before starting an event — `/actuator/info` for the backend,
      and a hard reload of the frontend (Vite hashes assets, so a cached
      `index.html` is the thing to watch for).

## 2. Build the images

```bash
docker compose build backend frontend
```

`docker/backend/Dockerfile` and `docker/frontend/Dockerfile` are the only build definitions; there is no registry push step wired up yet — that arrives with RFC-008.

## 3. Deploy

```bash
docker compose up -d
```

`backend` will not be marked healthy by its own healthcheck (`http://127.0.0.1:8080/actuator/health`) until Flyway migrations complete and the Spring context finishes starting — `start_period: 40s` gives it room. `frontend` depends on `backend`'s healthcheck passing before it starts.

## 4. Verify

- [ ] `/actuator/health` returns `UP` with both `db` and `realtime` components `UP` (see the [Runbook](runbook.md)).
- [ ] `/actuator/info` reports the expected `build.version` (confirms the right image is running, not a stale one).
- [ ] Run the golden-path smoke test from the [Production Checklist](production-checklist.md).
- [ ] Watch logs for a few minutes for any `internal.error` lines that weren't present before the deploy.

## 5. Rollback

There is no automated rollback. Revert to the previous known-good image tag and `docker compose up -d --force-recreate backend frontend`. If the deploy included a Flyway migration, confirm the previous application version's JPA entity mappings are still compatible with the migrated schema before rolling back — Flyway does not auto-revert migrations.
