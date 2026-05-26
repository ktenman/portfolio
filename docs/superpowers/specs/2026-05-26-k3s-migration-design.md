# K3s Migration Design

- **Date:** 2026-05-26
- **Status:** Approved (pre-implementation)
- **Branch:** `feature/k3s-migration`

## Goal

Migrate the portfolio stack from Docker Compose to single-node **k3s** with the
minimum number of changes. Prove the manifests locally on k3d first, then cut
over in-place on the `fov.ee` server with Docker Compose kept as an instant
rollback.

## Constraints

- **The server is tiny and dominates every decision:** 2 vCPU, 3.73 GiB RAM,
  38 GB disk (24 GB free), Debian 12, 4 GiB swap (mostly unused). Hetzner,
  `fov.ee → 135.181.145.200`.
- **The box is shared.** It also runs a host `transmission` daemon (`t.fov.ee`)
  and a `/srv/torrents` file server (`f.fov.ee`) routed through Caddy. The
  migration must not break these.
- **This is live financial data:** real portfolio, 200+ Flyway migrations of
  transaction history. Cutover must be reversible.
- The box **cannot run the full Compose stack and k3s + a second copy of the
  stack simultaneously** (no RAM headroom), so any in-place move implies a
  short downtime window.

## Current State

**Stack (9 Compose services, live RSS from `docker stats`):**

| Service                 | Image                             | RSS now     | Notes                            |
| ----------------------- | --------------------------------- | ----------- | -------------------------------- |
| backend                 | `ktenman/portfolio-be:latest`     | 832 MiB     | Spring Boot, `mem_limit 2g`      |
| redis                   | `redis:8-alpine`                  | **974 MiB** | **no `maxmemory` set — runaway** |
| auth                    | `ktenman/auth:latest`             | 383 MiB     | Spring Boot, sessions in Redis   |
| minio                   | `minio/minio`                     | 106 MiB     | logo storage                     |
| postgres                | `postgres:17-alpine`              | 88 MiB      | primary datastore                |
| cloudflare-bypass-proxy | `ktenman/cloudflare-bypass-proxy` | 42 MiB      | Trading212/Lightyear             |
| app (Caddy)             | `caddy:2.10-alpine`               | 24 MiB      | edge, owns 80/443                |
| frontend                | `ktenman/portfolio-fe:latest`     | 6 MiB       | nginx SPA                        |
| healthcheck             | `alpine`                          | 4.5 MiB     | docker.sock polling script       |

Total ≈ **2.46 GiB**.

**Deploy mechanism:** `ci.yml` → `deploy-pipeline.yml` → SSH `githubuser@fov.ee`
→ `scp docker-compose.yml/.env/Caddyfile/healthcheck.sh` → `docker compose pull && up -d`.

**Prior k3s attempt (stale, never executed):**

- `k8s/` has flat manifests for namespace, postgres, redis, backend, frontend,
  auth, caddy, caddy-config, secrets, healthcheck.
- `deploy-k3s.yml` exists but is **wired to nothing** (`ci.yml` only calls
  `deploy-pipeline.yml`). It would also fail: it `kubectl apply`s a
  `market-tracker.yaml` that does not exist and assumes k3s is already installed.
- Manifests have drifted from production: **no `minio`, no
  `cloudflare-bypass-proxy`**, `healthcheck.yaml` still probes a removed
  `market-tracker:5000`, `backend.yaml` is missing ~8 env vars, and
  `caddy-config.yaml` omits the `t.fov.ee`/`f.fov.ee` torrent routes.

## Design Decisions

| Decision         | Choice                                                          | Rationale                                                                                                                                                 |
| ---------------- | --------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Distribution     | **k3s** (single node)                                           | kubeadm's control plane alone wants ~2 GiB; k3s (sqlite-backed, single binary) fits a 3.7 GiB box.                                                        |
| Manifest layout  | **Flat manifests + envsubst** (not Helm/Kustomize)              | Minimum-change directive. Reuses existing `k8s/` + the envsubst flow the deploy workflows already use. Helm/Kustomize is an optional "v2" refactor later. |
| Edge             | **Keep Caddy**                                                  | It does sophisticated `forward_auth` routing; reimplementing as Traefik + cert-manager + forward-auth would be a large rewrite. Disable k3s Traefik.      |
| Service exposure | **Caddy `type: LoadBalancer` via k3s servicelb (klipper)**      | Already in `caddy.yaml`; klipper binds host 80/443. No extra components.                                                                                  |
| Local domain     | **`fov.test`** (RFC 6761 reserved)                              | Keeps real `fov.ee` reachable and unambiguous; never collides with a real domain.                                                                         |
| Local TLS        | **Caddy `tls internal`**                                        | No public reachability ⇒ no Let's Encrypt for any local domain. Self-signed from Caddy's CA; `curl -k` or one-time `caddy trust`.                         |
| Redis memory     | **Cap `--maxmemory 384mb --maxmemory-policy allkeys-lru`**      | Reclaims ~600 MiB; this is what makes k3s + the stack fit. Cap is high enough that tiny, hot auth-session keys are not evicted before stale cache.        |
| Healthcheck      | **Drop the pod; add a small CronJob** pinging `HEALTHCHECK_URL` | k8s liveness/readiness probes already replace the per-service polling; only the external dead-man's-switch ping is worth keeping.                         |

## Work Breakdown — fixing the stale manifests

1. **Add `k8s/minio.yaml`** — Deployment + PVC + Service.
2. **Add `k8s/cloudflare-bypass-proxy.yaml`** — Deployment + Service (:3000),
   `mem_limit` 256 Mi equivalent.
3. **Fix `k8s/backend.yaml`** — add missing env: `MINIO_*`, `TRADING212_*`,
   `OPENROUTER_API_KEY`, `CLOUDFLARE_BYPASS_PROXY_URL`, `SPRING_FLYWAY_ENABLED`;
   wire new secret keys; lower memory limit from 2 Gi to ~1.3 Gi.
4. **Fix `k8s/secrets.yaml`** — add `OPENROUTER_API_KEY`, `TRADING212_API_KEY_ID`,
   `TRADING212_API_KEY_SECRET`, `MINIO_ROOT_USER`, `MINIO_ROOT_PASSWORD`,
   `MINIO_BUCKET_NAME`.
5. **Replace `k8s/healthcheck.yaml`** — delete the broken Deployment; add a
   CronJob curling `HEALTHCHECK_URL` (optional, runs only if the secret is set).
6. **Sync Caddy config** — prod `caddy-config.yaml` gains the `t.fov.ee`
   (transmission) and `f.fov.ee` (`/srv/torrents`) routes; a separate local
   variant uses `fov.test` server names + `tls internal` and omits torrent routes.
7. **Add resource requests/limits** to postgres, redis, caddy (the others have them).
8. **Fix `deploy-k3s.yml`** — drop the `market-tracker.yaml` apply; add
   minio/proxy applies and their secret exports; add an idempotent k3s-install step.

## Memory Budget (post-migration estimate)

| Component                       | Request | Limit  | Expected RSS             |
| ------------------------------- | ------- | ------ | ------------------------ |
| backend                         | 512 Mi  | 1.3 Gi | ~830 Mi                  |
| redis (capped)                  | 128 Mi  | 512 Mi | ~384 Mi                  |
| auth                            | 256 Mi  | 512 Mi | ~383 Mi                  |
| minio                           | 64 Mi   | 256 Mi | ~106 Mi                  |
| postgres                        | 128 Mi  | 512 Mi | ~90 Mi                   |
| caddy                           | 32 Mi   | 128 Mi | ~24 Mi                   |
| proxy                           | 64 Mi   | 256 Mi | ~42 Mi                   |
| frontend                        | 32 Mi   | 64 Mi  | ~6 Mi                    |
| **workloads**                   |         |        | **~1.86 GiB**            |
| k3s control plane + system pods |         |        | ~0.6–0.7 GiB             |
| OS + containerd                 |         |        | ~0.3 GiB                 |
| **total**                       |         |        | **~2.9 GiB of 3.73 GiB** |

Comfortable, with 4 GiB swap as the safety net. The Redis cap is the decisive lever.

## Phase 1 — Local validation (k3d, zero risk to prod)

```bash
k3d cluster create portfolio \
  --port "80:80@loadbalancer" --port "443:443@loadbalancer" \
  --k3s-arg "--disable=traefik@server:0"
# /etc/hosts: 127.0.0.1 fov.test calculator.fov.test
```

- Apply a gitignored local secrets file (integrations blank/disabled; backend
  must still boot healthy). Set `TELEGRAM_BOT_ENABLED=false`, `TRADING212_ENABLED=false`.
- Apply all manifests with the **local Caddy config** (`fov.test`, `tls internal`).

**Success criteria:**

- Every pod reaches `Ready`.
- `curl -k https://fov.test/calculator` → HTTP 200 (Caddy → frontend → backend).
- `backend /actuator/health` → `UP` (DB + Redis + MinIO connected).
- Flyway migrations ran cleanly on the fresh PVC.
- Protected routes redirect to `/login` (correct; real Google OAuth not
  exercised locally because Google rejects `.test` redirect URIs).

## Phase 2 — In-place cutover on fov.ee (downtime window)

1. **Backup (rollback safety):** `pg_dump` live Postgres; copy MinIO data.
2. Install k3s: `curl -sfL https://get.k3s.io | INSTALL_K3S_EXEC="--disable traefik" sh -`;
   verify `kubectl get nodes`. Ensure kubelet tolerates swap.
3. **Downtime starts:** `docker compose down` — frees ~2.4 GiB + ports 80/443.
   **Compose files + `.env` stay in place for instant rollback.**
4. Restore Postgres/MinIO data into the new PVCs.
5. Apply manifests with the **prod Caddy config** (real domain, Let's Encrypt,
   torrent routes), Redis cap, resource limits.
6. Validate publicly: real Google login, calculator, torrent routes, valid certs.
7. **Cutover CI:** switch `ci.yml` from `deploy-pipeline` → `deploy-k3s`.

**Rollback:** `/usr/local/bin/k3s-killall.sh` (stops k3s, frees 80/443) +
`docker compose up -d`. DNS never changes (same box), so rollback is seconds.

## Non-goals (minimum-change discipline)

No Helm, no Kustomize, no Vue/backend code changes, no in-cluster registry, no
external managed DB, no monitoring stack, no service mesh. Postgres/Redis/MinIO
stay in-cluster on local-path PVCs.

## Open Risks / To Verify

- **Transmission route under k3s:** the prod `t.fov.ee → 172.17.0.1:9091` route
  assumes the _Docker_ bridge IP. k3s uses containerd; `172.17.0.1` will not
  exist. Caddy must reach host transmission via the node's real IP. Verify what
  transmission binds to (`0.0.0.0:9091` vs `172.17.0.1:9091`) and repoint.
- **Redis eviction vs auth sessions:** confirm session keys survive under
  `allkeys-lru` at 384 MiB; switch to `volatile-lru` if sessions get evicted.
- **Memory budget is tight (~2.9 GiB of 3.73 GiB):** swap must stay enabled;
  watch for OOMKills on first deploy and adjust limits.
- **Backend boot without integration keys (local):** confirm the backend
  reaches `UP` with Trading212/OpenRouter/Vision keys absent.
- **Let's Encrypt rate limits:** fresh Caddy certs re-issue on first prod apply;
  one issuance per domain, well within limits.
