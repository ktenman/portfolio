#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
[ -f .env.sync ] || { echo "Missing .env.sync (copy .env.sync.example)"; exit 1; }
set -a; . ./.env.sync; set +a
DUMP="$(mktemp -t portfolio-prod.XXXXXX.dump)"
trap 'rm -f "$DUMP"' EXIT

echo "Dumping production database..."
ssh "$SYNC_SSH" "docker exec $SYNC_CONTAINER sh -c 'pg_dump -U \$POSTGRES_USER -Fc $SYNC_DB'" > "$DUMP"
echo "Dump size: $(du -h "$DUMP" | cut -f1)"

echo "Restoring into local postgres-dev..."
docker compose -f compose.yaml up -d postgres-dev >/dev/null
docker compose -f compose.yaml exec -T postgres-dev psql -U postgres -d postgres -q \
  -c "DROP DATABASE IF EXISTS portfolio WITH (FORCE)" \
  -c "CREATE DATABASE portfolio"
docker compose -f compose.yaml exec -T postgres-dev pg_restore -U postgres -d portfolio --no-owner --no-privileges < "$DUMP"

echo "Flushing local Redis cache..."
docker compose -f compose.yaml exec -T redis-dev redis-cli FLUSHALL >/dev/null || true
echo "Done"
