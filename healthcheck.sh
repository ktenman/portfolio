#!/bin/sh

DISK_LIMIT=${DISK_LIMIT:-85}
SWAP_LIMIT=${SWAP_LIMIT:-50}

disk_percent() {
  df -P / | awk 'NR==2 {sub("%", "", $5); print $5}'
}

swap_percent() {
  awk '/^SwapTotal/ {total=$2} /^SwapFree/ {free=$2} END {print total ? int((total - free) * 100 / total) : 0}' "${MEMINFO:-/proc/meminfo}"
}

redis_saves() {
  printf 'INFO persistence\r\nQUIT\r\n' | nc -w 5 redis 6379 | grep -q 'rdb_last_bgsave_status:ok'
}

host_problem() {
  disk=$(disk_percent)
  swap=$(swap_percent)
  if [ "${disk}" -ge "${DISK_LIMIT}" ]; then
    echo "disk ${disk}% used"
  elif [ "${swap}" -ge "${SWAP_LIMIT}" ]; then
    echo "swap ${swap}% used"
  elif ! redis_saves; then
    echo "Redis cannot save"
  fi
}

if [ -n "${HEALTHCHECK_LIB:-}" ]; then
  return 0
fi

apk add --no-cache curl jq

if [ -z "${HEALTHCHECK_URL}" ]; then
  echo "Error: HEALTHCHECK_URL is not set"
  exit 1
fi

while true; do
  all_healthy=true
  for service in postgres redis backend frontend auth app; do
    health=$(curl -s --unix-socket /var/run/docker.sock http://localhost/containers/${service}/json | jq -r .State.Health.Status)
    echo "$(date): ${service} status: ${health}"
    if [ "${health}" != "healthy" ]; then
      all_healthy=false
    fi
  done
  problem=$(host_problem)
  if [ -n "${problem}" ]; then
    echo "$(date): host problem: ${problem}"
    all_healthy=false
  fi
  if ${all_healthy}; then
    echo "$(date): All services are healthy. Sending heartbeat"
    if curl -fsS -m 10 --retry 5 -o /dev/null "${HEALTHCHECK_URL}"; then
      echo "$(date): Health check ping sent successfully"
    else
      echo "$(date): Failed to send health check ping"
    fi
  else
    echo "$(date): Not all services are healthy. Skipping health check ping."
  fi
  sleep 60
done
