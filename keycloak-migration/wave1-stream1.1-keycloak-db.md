# Wave 1 - Stream 1.1: Setup Keycloak Database

## Objective

Create PostgreSQL database container configuration for Keycloak.

## Tasks

### 1. Add to docker-compose.local.yml

```yaml
keycloak-db:
  image: postgres:17-alpine
  container_name: portfolio-keycloak-db
  environment:
    POSTGRES_DB: keycloak
    POSTGRES_USER: keycloak
    POSTGRES_PASSWORD: ${KEYCLOAK_DB_PASSWORD}
  volumes:
    - keycloak_data:/var/lib/postgresql/data
  networks:
    - portfolio-network
  healthcheck:
    test: ['CMD-SHELL', 'pg_isready -U keycloak']
    interval: 10s
    timeout: 5s
    retries: 5
```

### 2. Add volume definition

```yaml
volumes:
  keycloak_data:
    driver: local
```

### 3. Test database

```bash
# Start only the database
docker-compose -f docker-compose.local.yml up -d keycloak-db

# Check logs
docker-compose -f docker-compose.local.yml logs keycloak-db

# Test connection
docker exec -it portfolio-keycloak-db psql -U keycloak -d keycloak -c "SELECT version();"

# Stop when verified
docker-compose -f docker-compose.local.yml down
```

### 4. Create backup script

```bash
#!/bin/bash
# keycloak-db-backup.sh
BACKUP_DIR="./backups/keycloak"
mkdir -p $BACKUP_DIR
docker exec portfolio-keycloak-db pg_dump -U keycloak keycloak | gzip > "$BACKUP_DIR/keycloak-$(date +%Y%m%d_%H%M%S).sql.gz"
```

## Validation

- [ ] PostgreSQL container starts successfully
- [ ] Can connect to database
- [ ] Health check passes
- [ ] Backup script created

## Output

Keycloak database ready for use.
