#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

# Reproducible local bring-up of the full stack on k3d (cluster: portfolio).
#
# Local-only deviations from prod (the committed manifests stay prod-correct):
#   - Custom app images (backend, frontend) are built natively for the local
#     arch and imported into k3d; imagePullPolicy is patched to Never so the
#     node uses them instead of pulling the amd64 :latest from Docker Hub.
#   - Telegram and Trading212 are disabled: their tokens are dummy locally and
#     Trading212 needs the amd64 proxy, which cannot run here.
#   - auth and cloudflare-bypass-proxy images are amd64-only; on Apple Silicon
#     they stay ImagePullBackOff. Caddy uses caddy-config.local.yaml, which
#     bypasses forward_auth so the app is reachable without the auth service.

export BUILD_HASH=local BUILD_TIME=local
set -a && . ./.env.local && set +a

if [ "${SKIP_BUILD:-0}" != "1" ]; then
  ./gradlew clean bootJar -x test
  docker build -t ktenman/portfolio-be:latest -f src/Dockerfile .
  docker build -t ktenman/portfolio-fe:latest -f ui/Dockerfile .
  k3d image import ktenman/portfolio-be:latest ktenman/portfolio-fe:latest -c portfolio
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

kubectl -n portfolio patch deployment backend -p '{"spec":{"template":{"spec":{"containers":[{"name":"backend","imagePullPolicy":"Never"}]}}}}'
kubectl -n portfolio patch deployment frontend -p '{"spec":{"template":{"spec":{"containers":[{"name":"frontend","imagePullPolicy":"Never"}]}}}}'
kubectl -n portfolio set env deployment/backend TELEGRAM_BOT_ENABLED=false TRADING212_ENABLED=false

kubectl -n portfolio rollout status deployment/backend --timeout=300s
kubectl -n portfolio rollout status deployment/frontend --timeout=180s
kubectl -n portfolio rollout status deployment/caddy --timeout=180s

echo
echo "Local stack up (auth + cloudflare-bypass-proxy are expected ImagePullBackOff on arm64)."
echo "Add to /etc/hosts once: 127.0.0.1 fov.test calculator.fov.test"
echo "Smoke: curl -k https://fov.test/calculator   |   curl -k https://fov.test/healthz"
