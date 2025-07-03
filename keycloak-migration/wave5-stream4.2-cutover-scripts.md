# Wave 5 - Stream 4.2: Prepare Cutover Scripts

## Objective

Create scripts and procedures for the final authentication cutover.

## Tasks

### 1. Create Backup Script

```bash
#!/bin/bash
# backup-before-cutover.sh

BACKUP_DIR="./backups/cutover-$(date +%Y%m%d_%H%M%S)"
mkdir -p "$BACKUP_DIR"

echo "Creating pre-cutover backup..."

# Backup Keycloak database
docker exec portfolio-keycloak-db pg_dump -U keycloak keycloak | \
  gzip > "$BACKUP_DIR/keycloak.sql.gz"

# Backup Redis data
docker exec portfolio-redis redis-cli BGSAVE
docker cp portfolio-redis:/data/dump.rdb "$BACKUP_DIR/redis-dump.rdb"

# Backup configurations
cp docker-compose.local.yml "$BACKUP_DIR/"
cp Caddyfile.local "$BACKUP_DIR/"
cp .env "$BACKUP_DIR/.env.backup"

# Export Keycloak realm
docker exec portfolio-keycloak \
  /opt/keycloak/bin/kc.sh export \
  --dir /tmp --realm portfolio

docker cp portfolio-keycloak:/tmp/portfolio-realm.json "$BACKUP_DIR/"

echo "Backup completed: $BACKUP_DIR"
```

### 2. Create Cutover Script

```bash
#!/bin/bash
# execute-cutover.sh

set -e

echo "Starting authentication cutover..."

# Update Caddyfile
cat > Caddyfile.local << 'EOF'
{
    auto_https off
}

:80 {
    # New OAuth2-Proxy forward auth
    forward_auth oauth2-proxy:4180 {
        uri /oauth2/auth
        copy_headers X-Forwarded-User X-Forwarded-Email X-Forwarded-Access-Token
    }

    # OAuth2-Proxy endpoints
    handle_path /oauth2/* {
        reverse_proxy oauth2-proxy:4180
    }

    # Rest of your existing config...
    handle /api/* {
        reverse_proxy backend:8081
    }

    handle {
        reverse_proxy frontend:61234
    }
}
EOF

# Reload Caddy
docker exec portfolio-caddy caddy reload --config /etc/caddy/Caddyfile

echo "Caddy configuration updated"

# Stop old auth service (if not using dual-auth)
docker-compose -f docker-compose.local.yml stop auth

echo "Cutover completed!"
```

### 3. Create Rollback Script

```bash
#!/bin/bash
# rollback-auth.sh

BACKUP_DIR="$1"

if [ -z "$BACKUP_DIR" ]; then
    echo "Usage: ./rollback-auth.sh <backup-directory>"
    exit 1
fi

echo "Rolling back to previous auth..."

# Restore Caddyfile
cp "$BACKUP_DIR/Caddyfile.local" .
docker exec portfolio-caddy caddy reload --config /etc/caddy/Caddyfile

# Restart old auth service
docker-compose -f docker-compose.local.yml up -d auth

# Stop OAuth2-Proxy if needed
docker-compose -f docker-compose.local.yml stop oauth2-proxy

echo "Rollback completed"
```

### 4. Create Health Check Script

```bash
#!/bin/bash
# health-check.sh

echo "=== Auth System Health Check ==="

# Check Keycloak
echo -n "Keycloak: "
curl -s http://localhost:8080/health/ready > /dev/null && echo "✓ OK" || echo "✗ FAIL"

# Check OAuth2-Proxy
echo -n "OAuth2-Proxy: "
curl -s http://localhost:4180/ping > /dev/null && echo "✓ OK" || echo "✗ FAIL"

# Check Redis
echo -n "Redis: "
docker exec portfolio-redis redis-cli ping > /dev/null && echo "✓ OK" || echo "✗ FAIL"

# Check Caddy
echo -n "Caddy: "
curl -s http://localhost/health > /dev/null && echo "✓ OK" || echo "✗ FAIL"

# Test auth flow
echo -n "Auth Flow: "
RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" http://localhost/api/instruments)
if [ "$RESPONSE" = "401" ] || [ "$RESPONSE" = "302" ]; then
    echo "✓ OK (Requires auth)"
else
    echo "✗ FAIL (Got $RESPONSE)"
fi

echo "=== Session Statistics ==="
LEGACY=$(docker exec portfolio-redis redis-cli --scan --pattern "spring:session:*" | wc -l)
OAUTH2=$(docker exec portfolio-redis redis-cli --scan --pattern "*oauth2*" | wc -l)
echo "Legacy sessions: $LEGACY"
echo "OAuth2 sessions: $OAUTH2"
```

### 5. Create User Communication

```markdown
# auth-migration-announcement.md

## Authentication System Upgrade

**When**: [DATE] at [TIME] UTC
**Expected Duration**: 5 minutes
**Impact**: You will need to log in again

### What's Changing

We're upgrading our authentication system to provide better security and features.

### What You Need to Do

1. After the upgrade, you'll be redirected to login
2. Click "Login with Google"
3. Use your authorized Google account
4. You'll be logged in for 30 days

### Benefits

- More secure authentication
- Faster login process
- Better session management
- Future support for additional features

### Questions?

Contact: [support-email]
```

### 6. Create Monitoring Dashboard

```bash
#!/bin/bash
# monitor-migration.sh

while true; do
    clear
    echo "=== Auth Migration Monitor ==="
    echo "Time: $(date)"
    echo ""

    # Service status
    echo "Services:"
    docker-compose -f docker-compose.local.yml ps --format "table {{.Service}}\t{{.Status}}"
    echo ""

    # Session counts
    echo "Sessions:"
    LEGACY=$(docker exec portfolio-redis redis-cli --scan --pattern "spring:session:*" | wc -l)
    OAUTH2=$(docker exec portfolio-redis redis-cli --scan --pattern "*oauth2*" | wc -l)
    echo "  Legacy: $LEGACY"
    echo "  OAuth2: $OAUTH2"
    echo "  Total: $((LEGACY + OAUTH2))"
    echo ""

    # Recent logs
    echo "Recent Auth Activity:"
    docker logs portfolio-oauth2-proxy --tail 5 2>&1 | grep -E "(Authenticated|Invalid)"

    sleep 5
done
```

## Validation

- [ ] Backup script tested
- [ ] Cutover script tested in dev
- [ ] Rollback script tested
- [ ] Health checks passing
- [ ] User communication prepared
- [ ] Monitoring ready

## Output

Complete cutover toolkit ready for production migration.
