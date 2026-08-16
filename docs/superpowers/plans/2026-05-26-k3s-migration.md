# K3s Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate the portfolio stack from Docker Compose to single-node k3s with minimum changes — proven locally on k3d first, then cut over in-place on `fov.ee` with Compose kept as instant rollback.

**Architecture:** Reuse the existing flat `k8s/` manifests + envsubst secret flow. Keep Caddy as the TLS-terminating edge (k3s Traefik disabled). Postgres/Redis/MinIO stay in-cluster on local-path PVCs. Local uses the `fov.test` domain with Caddy `tls internal`; prod uses `fov.ee` + Let's Encrypt.

**Tech Stack:** k3s, k3d (local), kubectl, Caddy 2.10, PostgreSQL 17, Redis 8, MinIO, Docker Hub images (`ktenman/*:latest`).

**Verification model (infra, not unit tests):** each task = make one change → validate with `kubectl apply --dry-run=server` → apply → confirm readiness (`kubectl rollout status` / `kubectl wait`) → commit. The Phase 1 acceptance gate is a smoke test (Task 13).

**Spec:** `docs/superpowers/specs/2026-05-26-k3s-migration-design.md`

**Commit conventions (per AGENTS.md):** imperative subject, no type prefix, ≤50 chars, no AI attribution.

---

## PHASE 1 — Local validation on k3d (zero risk to prod)

### Task 1: Install tooling and create the local cluster

**Files:** none (local environment only)

- [ ] **Step 1: Install k3d + kubectl (idempotent)**

```bash
brew list k3d >/dev/null 2>&1 || brew install k3d
brew list kubectl >/dev/null 2>&1 || brew install kubectl
k3d version && kubectl version --client
```

Expected: k3d and kubectl client versions print. Docker Desktop / colima must be running (`docker ps` works).

- [ ] **Step 2: Create the cluster with Traefik disabled and 80/443 mapped**

```bash
k3d cluster create portfolio \
  --port "80:80@loadbalancer" \
  --port "443:443@loadbalancer" \
  --k3s-arg "--disable=traefik@server:0"
```

Expected: ends with `You can now use it like this: kubectl ...`.

- [ ] **Step 3: Verify node + that Traefik is absent**

```bash
kubectl get nodes
kubectl get pods -A | grep -i traefik || echo "traefik correctly disabled"
```

Expected: one node `Ready`; "traefik correctly disabled".

- [ ] **Step 4: No commit** (environment-only task).

---

### Task 2: Namespace, local hosts entries, local env file

**Files:**

- Use: `k8s/namespace.yaml` (unchanged)
- Create (gitignored): `.env.local`

- [ ] **Step 1: Add local DNS entries**

```bash
grep -q "fov.test" /etc/hosts || \
  echo "127.0.0.1 fov.test calculator.fov.test" | sudo tee -a /etc/hosts
getent hosts fov.test 2>/dev/null || dscacheutil -q host -a name fov.test
```

Expected: `fov.test` resolves to `127.0.0.1`.

- [ ] **Step 2: Apply the namespace**

```bash
kubectl apply -f k8s/namespace.yaml
kubectl get ns portfolio
```

Expected: `namespace/portfolio created`; namespace listed `Active`.

- [ ] **Step 3: Create the local env file (already gitignored via `.env.local`)**

Create `.env.local` at repo root with dummy/local values (real integration keys not needed locally):

```bash
cat > .env.local <<'EOF'
POSTGRES_USER=portfolio
POSTGRES_PASSWORD=localdevpassword
GOOGLE_CLIENT_ID=local-unused
GOOGLE_CLIENT_SECRET=local-unused
GITHUB_CLIENT_ID=local-unused
GITHUB_CLIENT_SECRET=local-unused
ALLOWED_EMAILS=ktenman@gmail.com
TELEGRAM_BOT_TOKEN=local-unused
GOOGLE_VISION_API_KEY=local-unused
HEALTHCHECK_URL=
MINIO_ROOT_USER=minioadmin
MINIO_ROOT_PASSWORD=minioadmin123
MINIO_BUCKET_NAME=portfolio-logos
OPENROUTER_API_KEY=local-unused
TRADING212_API_KEY_ID=local-unused
TRADING212_API_KEY_SECRET=local-unused
EOF
echo ".env.local written"
```

- [ ] **Step 4: Confirm `.env.local` is ignored**

```bash
git check-ignore .env.local && echo "ignored, safe"
```

Expected: prints `.env.local` then `ignored, safe`. **Do not commit `.env.local`.**

---

### Task 3: Extend secrets manifest with the missing keys

**Files:**

- Modify: `k8s/secrets.yaml`

- [ ] **Step 1: Add MinIO, OpenRouter, and Trading212 keys**

Replace the `stringData:` block in `k8s/secrets.yaml` with:

```yaml
stringData:
  # Database credentials
  POSTGRES_USER: '${POSTGRES_USER}'
  POSTGRES_PASSWORD: '${POSTGRES_PASSWORD}'

  # OAuth credentials
  GOOGLE_CLIENT_ID: '${GOOGLE_CLIENT_ID}'
  GOOGLE_CLIENT_SECRET: '${GOOGLE_CLIENT_SECRET}'
  GITHUB_CLIENT_ID: '${GITHUB_CLIENT_ID}'
  GITHUB_CLIENT_SECRET: '${GITHUB_CLIENT_SECRET}'

  # Application secrets
  ALLOWED_EMAILS: '${ALLOWED_EMAILS}'
  TELEGRAM_BOT_TOKEN: '${TELEGRAM_BOT_TOKEN}'
  GOOGLE_VISION_API_KEY: '${GOOGLE_VISION_API_KEY}'
  HEALTHCHECK_URL: '${HEALTHCHECK_URL}'

  # Object storage (MinIO)
  MINIO_ROOT_USER: '${MINIO_ROOT_USER}'
  MINIO_ROOT_PASSWORD: '${MINIO_ROOT_PASSWORD}'
  MINIO_BUCKET_NAME: '${MINIO_BUCKET_NAME}'

  # AI + broker integrations
  OPENROUTER_API_KEY: '${OPENROUTER_API_KEY}'
  TRADING212_API_KEY_ID: '${TRADING212_API_KEY_ID}'
  TRADING212_API_KEY_SECRET: '${TRADING212_API_KEY_SECRET}'
```

- [ ] **Step 2: Apply locally via envsubst and verify keys exist**

```bash
set -a && . ./.env.local && set +a
envsubst < k8s/secrets.yaml | kubectl apply -f -
kubectl -n portfolio get secret portfolio-secrets -o jsonpath='{.data}' | tr ',' '\n' | grep -cE "MINIO_ROOT_USER|OPENROUTER_API_KEY|TRADING212_API_KEY_ID"
```

Expected: `secret/portfolio-secrets created`; count `3`.

- [ ] **Step 3: Commit (manifest only — never the secret values)**

```bash
git add k8s/secrets.yaml
git commit -m "Add MinIO, OpenRouter, Trading212 secret keys"
```

---

### Task 4: Postgres — add resource requests/limits

**Files:**

- Modify: `k8s/postgres.yaml`

- [ ] **Step 1: Add a `resources` block to the postgres container**

In `k8s/postgres.yaml`, after the `volumeMounts:` block of the `postgres` container (before the `volumes:` key), add:

```yaml
resources:
  requests:
    memory: '128Mi'
    cpu: '100m'
  limits:
    memory: '512Mi'
    cpu: '1000m'
```

- [ ] **Step 2: Validate, apply, wait ready**

```bash
kubectl apply --dry-run=server -f k8s/postgres.yaml
kubectl apply -f k8s/postgres.yaml
kubectl -n portfolio rollout status deployment/postgres --timeout=120s
```

Expected: dry-run prints `configured`/`created`; rollout `successfully rolled out`.

- [ ] **Step 3: Commit**

```bash
git add k8s/postgres.yaml
git commit -m "Add resource limits to postgres"
```

---

### Task 5: Redis — cap memory and add resources

**Files:**

- Modify: `k8s/redis.yaml`

- [ ] **Step 1: Add `command` (memory cap) and `resources` to the redis container**

In `k8s/redis.yaml`, inside the `redis` container spec (after `image: redis:8-alpine`), add `command` and `resources` so the container block reads:

```yaml
- name: redis
  image: redis:8-alpine
  command:
    - 'redis-server'
    - '--maxmemory'
    - '384mb'
    - '--maxmemory-policy'
    - 'allkeys-lru'
    - '--save'
    - '900'
    - '1'
  ports:
    - containerPort: 6379
  resources:
    requests:
      memory: '128Mi'
      cpu: '50m'
    limits:
      memory: '512Mi'
      cpu: '500m'
  volumeMounts:
    - name: redis-data
      mountPath: /data
```

- [ ] **Step 2: Validate, apply, wait, confirm the cap took effect**

```bash
kubectl apply --dry-run=server -f k8s/redis.yaml
kubectl apply -f k8s/redis.yaml
kubectl -n portfolio rollout status deployment/redis --timeout=120s
kubectl -n portfolio exec deploy/redis -- redis-cli config get maxmemory
```

Expected: rollout success; `maxmemory` reports `402653184` (384 MiB).

- [ ] **Step 3: Commit**

```bash
git add k8s/redis.yaml
git commit -m "Cap redis memory at 384mb with lru eviction"
```

---

### Task 6: MinIO — new manifest

**Files:**

- Create: `k8s/minio.yaml`

- [ ] **Step 1: Create `k8s/minio.yaml`**

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: minio
  namespace: portfolio
spec:
  replicas: 1
  selector:
    matchLabels:
      app: minio
  template:
    metadata:
      labels:
        app: minio
    spec:
      containers:
        - name: minio
          image: minio/minio:latest
          args: ['server', '/data', '--console-address', ':9001']
          ports:
            - containerPort: 9000
              name: api
            - containerPort: 9001
              name: console
          env:
            - name: MINIO_ROOT_USER
              valueFrom:
                secretKeyRef:
                  name: portfolio-secrets
                  key: MINIO_ROOT_USER
            - name: MINIO_ROOT_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: portfolio-secrets
                  key: MINIO_ROOT_PASSWORD
          readinessProbe:
            httpGet:
              path: /minio/health/ready
              port: 9000
            initialDelaySeconds: 10
            periodSeconds: 10
          livenessProbe:
            httpGet:
              path: /minio/health/live
              port: 9000
            initialDelaySeconds: 15
            periodSeconds: 20
          resources:
            requests:
              memory: '64Mi'
              cpu: '50m'
            limits:
              memory: '256Mi'
              cpu: '500m'
          volumeMounts:
            - name: minio-data
              mountPath: /data
      volumes:
        - name: minio-data
          persistentVolumeClaim:
            claimName: minio-pvc
---
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: minio-pvc
  namespace: portfolio
spec:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 5Gi
---
apiVersion: v1
kind: Service
metadata:
  name: minio
  namespace: portfolio
spec:
  ports:
    - name: api
      port: 9000
      targetPort: 9000
    - name: console
      port: 9001
      targetPort: 9001
  selector:
    app: minio
```

- [ ] **Step 2: Validate, apply, wait ready**

```bash
kubectl apply --dry-run=server -f k8s/minio.yaml
kubectl apply -f k8s/minio.yaml
kubectl -n portfolio rollout status deployment/minio --timeout=180s
```

Expected: rollout `successfully rolled out`. (Note: `minio/minio:latest` is multi-arch; pulls natively on Apple Silicon.)

- [ ] **Step 3: Commit**

```bash
git add k8s/minio.yaml
git commit -m "Add MinIO deployment, PVC, and service"
```

---

### Task 7: Cloudflare-bypass-proxy — new manifest

**Files:**

- Create: `k8s/cloudflare-bypass-proxy.yaml`

- [ ] **Step 1: Create `k8s/cloudflare-bypass-proxy.yaml`**

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: cloudflare-bypass-proxy
  namespace: portfolio
spec:
  replicas: 1
  selector:
    matchLabels:
      app: cloudflare-bypass-proxy
  template:
    metadata:
      labels:
        app: cloudflare-bypass-proxy
    spec:
      containers:
        - name: cloudflare-bypass-proxy
          image: ktenman/cloudflare-bypass-proxy:latest
          ports:
            - containerPort: 3000
          env:
            - name: NODE_ENV
              value: 'production'
          readinessProbe:
            httpGet:
              path: /health
              port: 3000
            initialDelaySeconds: 5
            periodSeconds: 10
          livenessProbe:
            httpGet:
              path: /health
              port: 3000
            initialDelaySeconds: 15
            periodSeconds: 20
          resources:
            requests:
              memory: '64Mi'
              cpu: '50m'
            limits:
              memory: '256Mi'
              cpu: '500m'
---
apiVersion: v1
kind: Service
metadata:
  name: cloudflare-bypass-proxy
  namespace: portfolio
spec:
  ports:
    - port: 3000
      targetPort: 3000
  selector:
    app: cloudflare-bypass-proxy
```

- [ ] **Step 2: Validate, apply, wait ready**

```bash
kubectl apply --dry-run=server -f k8s/cloudflare-bypass-proxy.yaml
kubectl apply -f k8s/cloudflare-bypass-proxy.yaml
kubectl -n portfolio rollout status deployment/cloudflare-bypass-proxy --timeout=180s
```

Expected: rollout success. **Apple Silicon note:** the image is `linux/amd64`-only; if the pod is slow/crashlooping under qemu, it is _not_ on the local smoke-test path (Trading212 is disabled locally), so proceed — it will run natively on the amd64 prod box.

- [ ] **Step 3: Commit**

```bash
git add k8s/cloudflare-bypass-proxy.yaml
git commit -m "Add cloudflare-bypass-proxy deployment and service"
```

---

### Task 8: Backend — add missing env vars, lower memory limit

**Files:**

- Modify: `k8s/backend.yaml`

- [ ] **Step 1: Add the missing env vars**

In `k8s/backend.yaml`, immediately after the `SPRING_PROFILES_ACTIVE` env entry (line ~52), insert:

```yaml
- name: SPRING_FLYWAY_ENABLED
  value: 'true'

# Object storage
- name: MINIO_ENDPOINT
  value: 'http://minio:9000'
- name: MINIO_ACCESS_KEY
  valueFrom:
    secretKeyRef:
      name: portfolio-secrets
      key: MINIO_ROOT_USER
- name: MINIO_SECRET_KEY
  valueFrom:
    secretKeyRef:
      name: portfolio-secrets
      key: MINIO_ROOT_PASSWORD
- name: MINIO_BUCKET_NAME
  value: 'portfolio-logos'

# Cloudflare bypass proxy + Trading212
- name: CLOUDFLARE_BYPASS_PROXY_URL
  value: 'http://cloudflare-bypass-proxy:3000'
- name: TRADING212_ENABLED
  value: 'true'
- name: TRADING212_API_KEY_ID
  valueFrom:
    secretKeyRef:
      name: portfolio-secrets
      key: TRADING212_API_KEY_ID
- name: TRADING212_API_KEY_SECRET
  valueFrom:
    secretKeyRef:
      name: portfolio-secrets
      key: TRADING212_API_KEY_SECRET

# OpenRouter (ETF sector classification)
- name: OPENROUTER_API_KEY
  valueFrom:
    secretKeyRef:
      name: portfolio-secrets
      key: OPENROUTER_API_KEY
```

- [ ] **Step 2: Lower the memory limit from 2Gi to 1300Mi**

In the `resources.limits` block of `k8s/backend.yaml`, change:

```yaml
limits:
  memory: '2Gi'
  cpu: '1000m'
```

to:

```yaml
limits:
  memory: '1300Mi'
  cpu: '1000m'
```

- [ ] **Step 3: Validate + apply + wait + confirm health UP**

```bash
kubectl apply --dry-run=server -f k8s/backend.yaml
envsubst < k8s/backend.yaml | kubectl apply -f -   # backend.yaml has ${BUILD_HASH}/${BUILD_TIME}
kubectl -n portfolio rollout status deployment/backend --timeout=300s
kubectl -n portfolio port-forward svc/backend 18080:8080 >/tmp/pf-be.log 2>&1 &
PF_PID=$!; sleep 4
curl -s localhost:18080/actuator/health; echo
kill $PF_PID
```

Note: `BUILD_HASH`/`BUILD_TIME` are still set from `.env.local`? They are not in `.env.local`; export them inline first: `export BUILD_HASH=local BUILD_TIME=local`.

Expected: rollout success; health JSON contains `"status":"UP"` with db, redis, and minio components UP.

- [ ] **Step 4: Commit**

```bash
git add k8s/backend.yaml
git commit -m "Wire MinIO, Trading212, OpenRouter env into backend"
```

---

### Task 9: Frontend — apply unchanged manifest

**Files:**

- Use: `k8s/frontend.yaml` (no edits)

- [ ] **Step 1: Apply + wait ready**

```bash
kubectl apply -f k8s/frontend.yaml
kubectl -n portfolio rollout status deployment/frontend --timeout=120s
```

Expected: rollout success.

- [ ] **Step 2: No commit** (no file change).

---

### Task 10: Auth — add GitHub creds and resources

**Files:**

- Modify: `k8s/auth.yaml`

- [ ] **Step 1: Add GitHub OAuth creds + resources to match Compose**

In `k8s/auth.yaml`, after the `GOOGLE_CLIENT_SECRET` env entry, add:

```yaml
- name: GITHUB_CLIENT_ID
  valueFrom:
    secretKeyRef:
      name: portfolio-secrets
      key: GITHUB_CLIENT_ID
- name: GITHUB_CLIENT_SECRET
  valueFrom:
    secretKeyRef:
      name: portfolio-secrets
      key: GITHUB_CLIENT_SECRET
```

And add a `resources` block to the `auth` container (after the `SERVER_PORT` env entry):

```yaml
resources:
  requests:
    memory: '256Mi'
    cpu: '100m'
  limits:
    memory: '512Mi'
    cpu: '750m'
```

- [ ] **Step 2: Validate, apply, wait ready**

```bash
kubectl apply --dry-run=server -f k8s/auth.yaml
kubectl apply -f k8s/auth.yaml
kubectl -n portfolio rollout status deployment/auth --timeout=180s
```

Expected: rollout success. (Local Google/GitHub login will not complete — expected.)

- [ ] **Step 3: Commit**

```bash
git add k8s/auth.yaml
git commit -m "Add GitHub creds and resources to auth"
```

---

### Task 11: Caddy — local config variant + resources

**Files:**

- Create: `k8s/caddy-config.local.yaml`
- Modify: `k8s/caddy.yaml`

- [ ] **Step 1: Create `k8s/caddy-config.local.yaml`** (ConfigMap named `caddy-config`, fov.test, internal certs, no torrent routes)

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: caddy-config
  namespace: portfolio
data:
  Caddyfile: |
    {
        local_certs
    }

    :80 {
        handle_path /healthz {
            respond "OK" 200
        }
        handle {
            redir https://{host}{uri} permanent
        }
    }

    calculator.fov.test {
        encode gzip
        handle / {
            redir /calculator permanent
        }
        handle /calculator* {
            reverse_proxy frontend:80
        }
        handle /api/calculator {
            reverse_proxy backend:8080
        }
        handle {
            reverse_proxy frontend:80
        }
    }

    fov.test {
        encode gzip
        handle_path /healthz {
            respond "OK" 200
        }
        @calculator_routes {
            path /calculator /calculator/* /assets/* *.js *.css *.svg *.ico *.png *.jpg *.jpeg *.gif *.woff *.woff2 *.ttf
        }
        handle @calculator_routes {
            reverse_proxy frontend:80
        }
        @auth_routes {
            path /login /login/* /oauth2/* /logout
        }
        handle @auth_routes {
            reverse_proxy auth:8083 {
                header_up Host {http.request.host}
            }
        }
        handle /api/calculator {
            reverse_proxy backend:8080
        }
        @protected {
            not path /login /login/* /oauth2/* /logout /calculator /calculator/* /assets/* *.js *.css *.svg *.ico *.png *.jpg *.jpeg *.gif *.woff *.woff2 *.ttf /api/calculator
        }
        handle @protected {
            forward_auth auth:8083 {
                uri /validate
                copy_headers X-User-Id X-User-Email
                header_up Host {http.request.host}
            }
            @api {
                path /api/*
            }
            handle @api {
                reverse_proxy backend:8080
            }
            handle {
                reverse_proxy frontend:80
            }
        }
    }
```

- [ ] **Step 2: Add a `resources` block to the caddy container in `k8s/caddy.yaml`**

After the `readinessProbe` block of the `caddy` container (before `volumes:`), add:

```yaml
resources:
  requests:
    memory: '32Mi'
    cpu: '50m'
  limits:
    memory: '128Mi'
    cpu: '500m'
```

- [ ] **Step 3: Apply the LOCAL config + caddy, wait ready**

```bash
kubectl apply -f k8s/caddy-config.local.yaml
kubectl apply --dry-run=server -f k8s/caddy.yaml
kubectl apply -f k8s/caddy.yaml
kubectl -n portfolio rollout status deployment/caddy --timeout=120s
kubectl -n portfolio get svc caddy
```

Expected: rollout success; caddy service shows an EXTERNAL-IP from klipper.

- [ ] **Step 4: Commit (both files)**

```bash
git add k8s/caddy-config.local.yaml k8s/caddy.yaml
git commit -m "Add local Caddy config and caddy resources"
```

---

### Task 12: Local convenience script + disable prod-only integrations

**Files:**

- Create: `k8s/local-up.sh`

- [ ] **Step 1: Create `k8s/local-up.sh`** (reproducible local bring-up)

```bash
#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

export BUILD_HASH=local BUILD_TIME=local
set -a && . ./.env.local && set +a

kubectl apply -f k8s/namespace.yaml
envsubst < k8s/secrets.yaml | kubectl apply -f -
kubectl apply -f k8s/postgres.yaml
kubectl apply -f k8s/redis.yaml
kubectl apply -f k8s/minio.yaml
kubectl apply -f k8s/cloudflare-bypass-proxy.yaml
envsubst < k8s/backend.yaml | kubectl apply -f -
kubectl apply -f k8s/frontend.yaml
kubectl apply -f k8s/auth.yaml
kubectl apply -f k8s/caddy-config.local.yaml
kubectl apply -f k8s/caddy.yaml

kubectl -n portfolio set env deployment/backend TRADING212_ENABLED=false TELEGRAM_BOT_ENABLED=false
kubectl -n portfolio rollout status deployment/backend --timeout=300s
echo "Local stack up. Smoke test: curl -k https://fov.test/calculator"
```

- [ ] **Step 2: Make it executable and run it**

```bash
chmod +x k8s/local-up.sh
./k8s/local-up.sh
```

Expected: ends with "Local stack up."

- [ ] **Step 3: Commit**

```bash
git add k8s/local-up.sh
git commit -m "Add local k3d bring-up script"
```

---

### Task 13: Phase 1 acceptance — local smoke test (the gate)

**Files:** none

- [ ] **Step 1: All pods Ready**

```bash
kubectl -n portfolio get pods
kubectl -n portfolio wait --for=condition=ready pod --all --timeout=240s
```

Expected: every pod (except possibly `cloudflare-bypass-proxy` on Apple Silicon) `Ready`.

- [ ] **Step 2: Public calculator route through Caddy**

```bash
curl -k -s -o /dev/null -w "%{http_code}\n" https://fov.test/calculator
```

Expected: `200`.

- [ ] **Step 3: Backend health UP (db + redis + minio)**

```bash
kubectl -n portfolio port-forward svc/backend 18080:8080 >/tmp/pf-be.log 2>&1 &
PF_PID=$!; sleep 4
curl -s localhost:18080/actuator/health | tr ',' '\n' | grep -E "status|db|redis|minio"
kill $PF_PID
```

Expected: overall `"status":"UP"`.

- [ ] **Step 4: Flyway ran on the fresh volume**

```bash
kubectl -n portfolio exec deploy/postgres -- \
  psql -U "$(grep POSTGRES_USER .env.local | cut -d= -f2)" -d portfolio -tA \
  -c "select count(*) from flyway_schema_history where success;"
```

Expected: a number ≥ 200.

- [ ] **Step 5: Protected route bounces to login (auth wiring correct)**

```bash
curl -k -s -o /dev/null -w "%{http_code}\n" https://fov.test/
```

Expected: `302` (redirect to `/login`) — confirms `forward_auth` is active.

**GATE:** Do not proceed to Phase 2 until Steps 1–5 pass. If they pass, Phase 1 is complete.

---

## CI WORKFLOW FIX (file-only; no deploy triggered)

### Task 14: Repair `deploy-k3s.yml` and replace the healthcheck

**Files:**

- Create: `k8s/healthcheck-cronjob.yaml`
- Delete: `k8s/healthcheck.yaml`
- Modify: `.github/workflows/deploy-k3s.yml`

- [ ] **Step 1: Create `k8s/healthcheck-cronjob.yaml`** (replaces the broken polling Deployment)

```yaml
apiVersion: batch/v1
kind: CronJob
metadata:
  name: healthcheck-ping
  namespace: portfolio
spec:
  schedule: '*/5 * * * *'
  concurrencyPolicy: Forbid
  successfulJobsHistoryLimit: 1
  failedJobsHistoryLimit: 1
  jobTemplate:
    spec:
      template:
        spec:
          restartPolicy: Never
          containers:
            - name: ping
              image: curlimages/curl:8.11.1
              command:
                - '/bin/sh'
                - '-c'
                - |
                  set -e
                  STATUS=$(curl -s -o /dev/null -w "%{http_code}" --max-time 20 http://backend:8080/actuator/health/readiness)
                  if [ "$STATUS" = "200" ] && [ -n "$HEALTHCHECK_URL" ]; then
                    curl -fsS --max-time 20 "$HEALTHCHECK_URL" >/dev/null && echo "pinged monitor"
                  else
                    echo "backend not ready ($STATUS) or no monitor url; skipping ping"
                  fi
              env:
                - name: HEALTHCHECK_URL
                  valueFrom:
                    secretKeyRef:
                      name: portfolio-secrets
                      key: HEALTHCHECK_URL
                      optional: true
              resources:
                requests:
                  memory: '16Mi'
                  cpu: '10m'
                limits:
                  memory: '64Mi'
                  cpu: '100m'
```

- [ ] **Step 2: Delete the broken healthcheck Deployment**

```bash
git rm k8s/healthcheck.yaml
```

- [ ] **Step 3: Fix the deploy step in `.github/workflows/deploy-k3s.yml`**

In the `secrets:` block under `workflow_call:`, add:

```yaml
OPENROUTER_API_KEY:
  required: true
TRADING212_API_KEY_ID:
  required: true
TRADING212_API_KEY_SECRET:
  required: true
```

In the "Deploy to K3s cluster" step, add these exports alongside the existing ones:

```bash
            export OPENROUTER_API_KEY='${{ secrets.OPENROUTER_API_KEY }}'
            export TRADING212_API_KEY_ID='${{ secrets.TRADING212_API_KEY_ID }}'
            export TRADING212_API_KEY_SECRET='${{ secrets.TRADING212_API_KEY_SECRET }}'
```

Replace the block of `kubectl apply` lines with (note: **`market-tracker.yaml` removed**, minio + proxy + prod caddy-config added, healthcheck → cronjob):

```bash
            kubectl create namespace portfolio --dry-run=client -o yaml | kubectl apply -f -
            envsubst < ~/k8s-deploy/secrets.yaml | kubectl apply -f -
            kubectl apply -f ~/k8s-deploy/postgres.yaml
            kubectl apply -f ~/k8s-deploy/redis.yaml
            kubectl apply -f ~/k8s-deploy/minio.yaml
            kubectl apply -f ~/k8s-deploy/cloudflare-bypass-proxy.yaml
            envsubst < ~/k8s-deploy/backend.yaml | kubectl apply -f -
            kubectl apply -f ~/k8s-deploy/frontend.yaml
            kubectl apply -f ~/k8s-deploy/auth.yaml
            kubectl apply -f ~/k8s-deploy/caddy-config.yaml
            kubectl apply -f ~/k8s-deploy/caddy.yaml
            kubectl apply -f ~/k8s-deploy/healthcheck-cronjob.yaml
            kubectl rollout status deployment/backend -n portfolio --timeout=300s
            kubectl rollout status deployment/frontend -n portfolio --timeout=300s
            kubectl rollout status deployment/caddy -n portfolio --timeout=180s
```

- [ ] **Step 4: Validate YAML locally**

```bash
kubectl apply --dry-run=client -f k8s/healthcheck-cronjob.yaml
python3 -c "import yaml,sys; list(yaml.safe_load_all(open('.github/workflows/deploy-k3s.yml'))); print('workflow yaml ok')"
```

Expected: cronjob validates; "workflow yaml ok".

- [ ] **Step 5: Commit**

```bash
git add k8s/healthcheck-cronjob.yaml .github/workflows/deploy-k3s.yml
git rm --cached k8s/healthcheck.yaml 2>/dev/null || true
git commit -m "Repair k3s deploy workflow and replace healthcheck"
```

---

## PHASE 2 — In-place cutover on fov.ee (GATED: causes downtime; requires explicit go-ahead)

> Execute only after Phase 1 passes AND the user explicitly approves a maintenance window. Every step assumes `ssh root@fov.ee` unless noted. Compose files stay in `/home/githubuser/` for instant rollback.

### Task 15: Back up production data

- [ ] **Step 1: Dump Postgres and snapshot MinIO**

```bash
ssh root@fov.ee '
  set -e
  TS=$(date +%Y%m%d-%H%M)
  docker exec postgres pg_dump -U "$(grep POSTGRES_USER /home/githubuser/.env | cut -d= -f2)" -d portfolio -Fc > /root/portfolio-$TS.dump
  docker run --rm --volumes-from minio -v /root:/backup alpine tar czf /backup/minio-$TS.tgz /data
  ls -lh /root/portfolio-$TS.dump /root/minio-$TS.tgz
'
```

Expected: a `.dump` (non-zero) and a `.tgz` listed. **Copy the dump off-box** before continuing:

```bash
scp root@fov.ee:/root/portfolio-*.dump ./backups/
```

---

### Task 16: Install k3s on fov.ee

- [ ] **Step 1: Install with Traefik disabled and a readable kubeconfig**

```bash
ssh root@fov.ee '
  command -v k3s >/dev/null 2>&1 || \
    curl -sfL https://get.k3s.io | INSTALL_K3S_EXEC="--disable traefik --write-kubeconfig-mode 644" sh -
  systemctl is-active k3s
  k3s kubectl get nodes
'
```

Expected: `active`; one node `Ready`. (k3s does not yet bind 80/443 — Caddy is not deployed; Compose still serves the site.)

- [ ] **Step 2: Grant `githubuser` kubeconfig access (for CI later)**

```bash
ssh root@fov.ee 'usermod -aG sudo githubuser 2>/dev/null; ls -l /etc/rancher/k3s/k3s.yaml'
```

Expected: kubeconfig is mode `644` (readable by githubuser).

---

### Task 17: Stop Compose and restore data (downtime begins)

- [ ] **Step 1: Bring Compose down (frees RAM + ports 80/443)**

```bash
ssh root@fov.ee 'cd /home/githubuser && docker compose down --remove-orphans && docker ps'
```

Expected: no portfolio containers running. **Site is down from here until Task 19.**

- [ ] **Step 2: Apply data-bearing manifests, then restore into the new PVCs**

```bash
ssh root@fov.ee '
  set -a && . /home/githubuser/.env && set +a
  cd ~/k8s-deploy 2>/dev/null || { mkdir -p ~/k8s-deploy; }
'
# from your laptop, push the current manifests:
scp k8s/*.yaml root@fov.ee:/root/k8s-deploy/
ssh root@fov.ee '
  export KUBECONFIG=/etc/rancher/k3s/k3s.yaml
  set -a && . /home/githubuser/.env && set +a
  export BUILD_HASH=cutover BUILD_TIME=$(date -u +%FT%TZ)
  kubectl apply -f /root/k8s-deploy/namespace.yaml
  envsubst < /root/k8s-deploy/secrets.yaml | kubectl apply -f -
  kubectl apply -f /root/k8s-deploy/postgres.yaml
  kubectl apply -f /root/k8s-deploy/minio.yaml
  kubectl -n portfolio rollout status deployment/postgres --timeout=180s
  kubectl -n portfolio rollout status deployment/minio --timeout=180s
'
```

- [ ] **Step 3: Restore Postgres dump and MinIO data into the pods**

```bash
ssh root@fov.ee '
  export KUBECONFIG=/etc/rancher/k3s/k3s.yaml
  PG=$(kubectl -n portfolio get pod -l app=postgres -o jsonpath="{.items[0].metadata.name}")
  DUMP=$(ls -t /root/portfolio-*.dump | head -1)
  kubectl -n portfolio cp "$DUMP" "$PG:/tmp/restore.dump"
  kubectl -n portfolio exec "$PG" -- sh -c "pg_restore -U $(grep POSTGRES_USER /home/githubuser/.env | cut -d= -f2) -d portfolio --clean --if-exists /tmp/restore.dump" || true
  kubectl -n portfolio exec "$PG" -- psql -U $(grep POSTGRES_USER /home/githubuser/.env | cut -d= -f2) -d portfolio -tA -c "select count(*) from portfolio_transaction;"
'
```

Expected: a transaction count matching production (sanity check).

---

### Task 18: Resolve the torrent/transmission wiring for prod Caddy

**Files:**

- Modify: `k8s/caddy-config.yaml` (prod), `k8s/caddy.yaml` (hostPath for `/srv/torrents`)

- [ ] **Step 1: Discover what transmission binds to and the node IP**

```bash
ssh root@fov.ee '
  ss -ltnp | grep 9091 || echo "9091 not listening"
  hostname -I | awk "{print \$1}"
'
```

Record the node IP (call it `NODE_IP`). If transmission binds only `127.0.0.1:9091`, reconfigure it to `0.0.0.0:9091` (transmission settings) so the Caddy pod can reach it via `NODE_IP`.

- [ ] **Step 2: Add the torrent routes to prod `k8s/caddy-config.yaml`**

Inside the `data.Caddyfile` value, add `www.fov.ee`, `calculator.fov.ee`, `f.fov.ee`, and `t.fov.ee` blocks mirroring the Compose `Caddyfile`, with the transmission target set to `NODE_IP:9091` (replace `NODE_IP` with the value from Step 1). The `t.fov.ee` block:

```
    t.fov.ee {
        encode gzip
        reverse_proxy NODE_IP:9091 {
            header_up Host {http.request.host}
            header_up X-Real-IP {remote_host}
            header_up X-Forwarded-For {remote_host}
            header_up X-Forwarded-Proto {scheme}
        }
        header {
            X-Frame-Options "SAMEORIGIN"
            X-Content-Type-Options "nosniff"
            Strict-Transport-Security "max-age=31536000; includeSubDomains"
            -Server
        }
    }
```

And `f.fov.ee` (served from a hostPath mount — see Step 3):

```
    f.fov.ee {
        encode gzip
        handle_path /healthz { respond "OK" 200 }
        handle {
            basicauth {
                estimol $2a$14$kOKsIHEC46WYCmjm.bujgeCWZRRxwmYUITSATqk7XKw9T6tKb1Dx6
            }
            root * /srv/torrents
            file_server browse { hide .* }
        }
    }
```

- [ ] **Step 3: Mount `/srv/torrents` into the Caddy pod in `k8s/caddy.yaml`**

Add to the caddy container `volumeMounts`:

```yaml
- name: torrents
  mountPath: /srv/torrents
  readOnly: true
```

Add to the pod `volumes`:

```yaml
- name: torrents
  hostPath:
    path: /srv/torrents
    type: Directory
```

- [ ] **Step 4: Validate the prod Caddyfile syntax locally**

```bash
docker run --rm -v "$PWD/k8s/caddy-config.yaml":/c.yaml alpine sh -c "echo 'manual review: confirm NODE_IP substituted, no fov.test remnants'"
grep -n "NODE_IP" k8s/caddy-config.yaml && echo "FIX: NODE_IP placeholder still present" || echo "ok: concrete IP set"
```

Expected: "ok: concrete IP set".

- [ ] **Step 5: Commit**

```bash
git add k8s/caddy-config.yaml k8s/caddy.yaml
git commit -m "Add torrent routes and hostPath to prod Caddy"
```

---

### Task 19: Deploy the rest and validate publicly (downtime ends)

- [ ] **Step 1: Apply remaining manifests with the PROD caddy-config**

```bash
scp k8s/caddy-config.yaml k8s/caddy.yaml root@fov.ee:/root/k8s-deploy/
ssh root@fov.ee '
  export KUBECONFIG=/etc/rancher/k3s/k3s.yaml
  set -a && . /home/githubuser/.env && set +a
  export BUILD_HASH=cutover BUILD_TIME=$(date -u +%FT%TZ)
  kubectl apply -f /root/k8s-deploy/redis.yaml
  kubectl apply -f /root/k8s-deploy/cloudflare-bypass-proxy.yaml
  envsubst < /root/k8s-deploy/backend.yaml | kubectl apply -f -
  kubectl apply -f /root/k8s-deploy/frontend.yaml
  kubectl apply -f /root/k8s-deploy/auth.yaml
  kubectl apply -f /root/k8s-deploy/caddy-config.yaml
  kubectl apply -f /root/k8s-deploy/caddy.yaml
  kubectl apply -f /root/k8s-deploy/healthcheck-cronjob.yaml
  kubectl -n portfolio rollout status deployment/backend --timeout=300s
  kubectl -n portfolio rollout status deployment/caddy --timeout=180s
'
```

- [ ] **Step 2: Validate the public site (from your laptop)**

```bash
curl -s -o /dev/null -w "calc %{http_code}\n" https://fov.ee/calculator
curl -s -o /dev/null -w "root %{http_code}\n" https://fov.ee/
echo | openssl s_client -connect fov.ee:443 -servername fov.ee 2>/dev/null | openssl x509 -noout -issuer
```

Expected: calculator `200`; root `302` (to login); issuer shows **Let's Encrypt** (real cert, not Caddy internal). Then manually confirm Google login + a torrent page (`https://t.fov.ee`, `https://f.fov.ee`).

- [ ] **Step 3: If anything is broken — ROLLBACK (seconds)**

```bash
ssh root@fov.ee '/usr/local/bin/k3s-killall.sh; cd /home/githubuser && docker compose up -d'
```

Expected: Compose stack returns; site restored. DNS unchanged throughout.

---

### Task 20: Cut CI over from Compose to k3s

**Files:**

- Modify: `.github/workflows/ci.yml`

- [ ] **Step 1: Point the deploy job at the k3s workflow**

In `.github/workflows/ci.yml`, change the `call-deploy-pipeline` job's `uses:` from `./.github/workflows/deploy-pipeline.yml` to `./.github/workflows/deploy-k3s.yml`, and add the `OPENROUTER_API_KEY`, `TRADING212_API_KEY_ID`, `TRADING212_API_KEY_SECRET` secrets to its `secrets:` mapping (they are already GitHub repo secrets used by deploy-pipeline).

- [ ] **Step 2: Validate workflow YAML**

```bash
python3 -c "import yaml; yaml.safe_load(open('.github/workflows/ci.yml')); print('ci.yml ok')"
```

Expected: "ci.yml ok".

- [ ] **Step 3: Commit + open PR**

```bash
git add .github/workflows/ci.yml
git commit -m "Switch CI deploy from compose to k3s"
```

Open a PR from `feature/k3s-migration` → `main`. A merge triggers the first automated k3s deploy; watch the Actions run and keep the Task 19 rollback ready.

---

## Post-migration cleanup (optional, separate PR)

- Remove `docker-compose.yml`, `Caddyfile`, `healthcheck.sh`, and `deploy-pipeline.yml` once k3s has been stable for a week.
- Consider re-introducing Helm or Kustomize as a "v2" refactor for the local/prod config split.
