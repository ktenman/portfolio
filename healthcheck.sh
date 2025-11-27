#!/bin/sh

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
  if ${all_healthy}; then
    echo "$(date): All services are healthy. Sending ping to ${HEALTHCHECK_URL}"
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
