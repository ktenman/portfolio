#!/bin/sh
set -eu

SERVICES="postgres redis seaweedfs backend frontend auth app cloudflare-bypass-proxy"
DISK_LIMIT=${DISK_LIMIT:-90}
SWAP_LIMIT=${SWAP_LIMIT:-75}
LOG_DIR=${LOG_DIR:-$HOME/deploy-logs}

preflight() {
  disk=$(df -P / | awk 'NR==2 {sub("%", "", $5); print $5}')
  swap=$(awk '/^SwapTotal/ {total=$2} /^SwapFree/ {free=$2} END {print total ? int((total - free) * 100 / total) : 0}' /proc/meminfo)
  echo "Disk ${disk}% used, swap ${swap}% used"
  if [ "${disk}" -ge "${DISK_LIMIT}" ] || [ "${swap}" -ge "${SWAP_LIMIT}" ]; then
    echo "Refusing to deploy: limits are disk ${DISK_LIMIT}% and swap ${SWAP_LIMIT}%"
    exit 1
  fi
  mkdir -p "${LOG_DIR}"
  for service in ${SERVICES}; do
    docker logs --tail 5000 "${service}" > "${LOG_DIR}/${service}.log" 2>&1 || true
  done
}

healthy() {
  for service in ${SERVICES}; do
    health=$(docker inspect -f '{{.State.Health.Status}}' "${service}" 2>/dev/null || true)
    if [ "${health}" != healthy ]; then
      echo "${service} is ${health:-missing}"
      return 1
    fi
  done
  [ "$(docker inspect -f '{{.State.Running}}' healthcheck 2>/dev/null)" = true ] || { echo "healthcheck is not running"; return 1; }
}

"$1"
