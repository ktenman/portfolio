# Wave 2 - Stream 1.2: Deploy Keycloak Service

## Objective

Deploy Keycloak container with proper configuration.

## Tasks

### 1. Add Keycloak to docker-compose.local.yml

```yaml
keycloak:
  image: quay.io/keycloak/keycloak:25.0
  container_name: portfolio-keycloak
  environment:
    KC_DB: postgres
    KC_DB_URL: jdbc:postgresql://keycloak-db:5432/keycloak
    KC_DB_USERNAME: keycloak
    KC_DB_PASSWORD: ${KEYCLOAK_DB_PASSWORD}
    KC_HOSTNAME: localhost
    KC_HOSTNAME_PORT: 8080
    KC_HOSTNAME_STRICT: false
    KC_HTTP_ENABLED: true
    KC_HOSTNAME_STRICT_HTTPS: false
    KEYCLOAK_ADMIN: admin
    KEYCLOAK_ADMIN_PASSWORD: ${KEYCLOAK_ADMIN_PASSWORD}
  command: start-dev
  ports:
    - '8080:8080'
  depends_on:
    keycloak-db:
      condition: service_healthy
  networks:
    - portfolio-network
  healthcheck:
    test: ['CMD', 'curl', '-f', 'http://localhost:8080/health/ready']
    interval: 30s
    timeout: 10s
    retries: 10
    start_period: 60s
```

### 2. Start Keycloak

```bash
# Start database and Keycloak
docker-compose -f docker-compose.local.yml up -d keycloak-db keycloak

# Monitor startup logs
docker-compose -f docker-compose.local.yml logs -f keycloak

# Wait for "Keycloak 25.0.0 on JVM (powered by Quarkus) started"
```

### 3. Verify deployment

```bash
# Check health endpoint
curl http://localhost:8080/health/ready

# Access admin console
open http://localhost:8080/admin

# Login with admin credentials from .env
```

### 4. Initial configuration

```bash
# Export realm configuration template
cat > realm-template.json << 'EOF'
{
  "realm": "portfolio",
  "enabled": true,
  "sslRequired": "external",
  "registrationAllowed": false,
  "loginWithEmailAllowed": true,
  "duplicateEmailsAllowed": false,
  "resetPasswordAllowed": true,
  "editUsernameAllowed": false,
  "bruteForceProtected": true
}
EOF
```

## Validation

- [ ] Keycloak starts successfully
- [ ] Admin console accessible
- [ ] Can login with admin credentials
- [ ] Health check passes

## Output

Keycloak running and ready for configuration.
