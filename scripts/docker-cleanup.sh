#!/bin/bash
LOG_FILE=/var/log/docker-cleanup.log
echo "[$(date '+%Y-%m-%d %H:%M:%S')] Starting cleanup..." >> $LOG_FILE

BEFORE=$(df / --output=avail | tail -1)

journalctl --vacuum-size=100M >> $LOG_FILE 2>&1
find /var/lib/docker/volumes/githubuser_redis_data/_data -maxdepth 1 -name 'temp-*.rdb' -mmin +60 -delete >> $LOG_FILE 2>&1

docker container prune -f --filter until=1h >> $LOG_FILE 2>&1

for builder in $(docker ps -a --filter 'name=buildx_buildkit' --filter 'status=exited' --format '{{.Names}}' 2>/dev/null); do
  echo "Removing stale builder: $builder" >> $LOG_FILE
  docker rm $builder >> $LOG_FILE 2>&1
done

docker volume prune -f >> $LOG_FILE 2>&1
docker builder prune -af >> $LOG_FILE 2>&1
docker image prune -af >> $LOG_FILE 2>&1

AFTER=$(df / --output=avail | tail -1)
FREED=$(( (AFTER - BEFORE) / 1024 ))
echo "[$(date '+%Y-%m-%d %H:%M:%S')] Cleanup complete. Freed: ${FREED}MB" >> $LOG_FILE
echo "---" >> $LOG_FILE
