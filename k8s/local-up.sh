#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

# Reproducible local bring-up of the full stack on k3d (cluster: portfolio).
#
# Local-only deviations from prod (the committed manifests stay prod-correct):
#   - Custom app images (backend, frontend) are built natively for the local
#     arch and imported into k3d; imagePullPolicy is patched to Never so the
#     node uses them instead of pulling the multi-arch :latest from Docker Hub.
#   - cloudflare-bypass-proxy ships amd64-only (curl-impersonate). For the local
#     arm64 node it is re-tagged as arm64 over the amd64 layers and imported, so
#     containerd will schedule it and the kernel qemu/binfmt handler emulates the
#     x86_64 binaries at runtime. Prod keeps the real amd64 image.
#   - Telegram and Trading212 are disabled: the Telegram token is dummy (and a
#     real one would hijack the prod bot webhook) and Trading212 needs a real
#     API key. Lightyear market data does work through the emulated proxy.
#   - auth runs (its image is arm64) but uses prod OAuth (REDIRECT_URI=fov.ee),
#     so login cannot complete locally; caddy-config.local.yaml bypasses
#     forward_auth so the app is reachable.

export BUILD_HASH=local BUILD_TIME=local
set -a && . ./.env.local && set +a

if [ "${SKIP_BUILD:-0}" != "1" ]; then
  ./gradlew clean bootJar -x test
  docker build -t ktenman/portfolio-be:latest -f src/Dockerfile .
  docker build -t ktenman/portfolio-fe:latest -f ui/Dockerfile .
  k3d image import ktenman/portfolio-be:latest ktenman/portfolio-fe:latest -c portfolio
  docker pull --platform linux/amd64 ktenman/cloudflare-bypass-proxy:latest
  docker buildx build --platform linux/arm64 --provenance=false -t ktenman/cloudflare-bypass-proxy:latest --load - <<'DOCKERFILE'
FROM --platform=linux/amd64 ktenman/cloudflare-bypass-proxy:latest
DOCKERFILE
  k3d image import ktenman/cloudflare-bypass-proxy:latest -c portfolio
fi

kubectl apply -f k8s/namespace.yaml
envsubst < k8s/secrets.yaml | kubectl apply -f -
kubectl apply -f k8s/postgres.yaml
kubectl apply -f k8s/redis.yaml
kubectl apply -f k8s/minio.yaml
kubectl apply -f k8s/cloudflare-bypass-proxy.yaml
envsubst '${BUILD_HASH} ${BUILD_TIME}' < k8s/backend.yaml | kubectl apply -f -
kubectl apply -f k8s/frontend.yaml
kubectl apply -f k8s/auth.yaml
kubectl apply -f k8s/caddy-config.local.yaml
kubectl apply -f k8s/caddy.yaml
kubectl apply -f k8s/healthcheck-cronjob.yaml

kubectl -n portfolio patch deployment backend -p '{"spec":{"template":{"spec":{"containers":[{"name":"backend","imagePullPolicy":"Never"}]}}}}'
kubectl -n portfolio patch deployment frontend -p '{"spec":{"template":{"spec":{"containers":[{"name":"frontend","imagePullPolicy":"Never"}]}}}}'
kubectl -n portfolio patch deployment cloudflare-bypass-proxy -p '{"spec":{"template":{"spec":{"containers":[{"name":"cloudflare-bypass-proxy","imagePullPolicy":"Never"}]}}}}'
kubectl -n portfolio set env deployment/backend TELEGRAM_BOT_ENABLED=false TRADING212_ENABLED=false

kubectl -n portfolio rollout status deployment/backend --timeout=300s
kubectl -n portfolio rollout status deployment/frontend --timeout=180s
kubectl -n portfolio rollout status deployment/cloudflare-bypass-proxy --timeout=240s
kubectl -n portfolio rollout status deployment/caddy --timeout=180s

echo
echo "Local stack up - all services running (cloudflare-bypass-proxy runs under qemu emulation; auth runs but uses prod OAuth, so caddy-config.local.yaml bypasses login)."
echo "Add to /etc/hosts once: 127.0.0.1 fov.test calculator.fov.test"
echo "Smoke: curl -k https://fov.test/calculator   |   curl -k https://fov.test/healthz"
