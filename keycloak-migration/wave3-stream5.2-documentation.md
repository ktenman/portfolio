# Wave 3 - Stream 5.2: Update Documentation

## Objective

Update all documentation to reflect new Keycloak-based authentication.

## Tasks

### 1. Update CLAUDE.md

Add new section for authentication:

```markdown
### Authentication System

The portfolio application uses Keycloak as the identity provider with OAuth2-Proxy handling session management.

**Architecture:**

- **Keycloak**: Identity and access management (port 8080)
- **OAuth2-Proxy**: OIDC client and session manager (port 4180)
- **Redis**: Session storage for OAuth2-Proxy
- **Caddy**: Reverse proxy with forward_auth

**Key Commands:**
\`\`\`bash

# Start auth infrastructure

docker-compose -f docker-compose.local.yml up -d keycloak-db keycloak oauth2-proxy

# Access Keycloak admin

open http://localhost:8080/admin

# View OAuth2-Proxy logs

docker-compose -f docker-compose.local.yml logs -f oauth2-proxy

# Check sessions in Redis

docker exec portfolio-redis redis-cli KEYS "_oauth2_"
\`\`\`

**Authentication Flow:**

1. User accesses protected resource
2. Caddy forward_auth checks with OAuth2-Proxy
3. OAuth2-Proxy validates session in Redis
4. If invalid, redirects to Keycloak login
5. User authenticates with Google
6. OAuth2-Proxy creates session in Redis
7. User accesses protected resource
```

### 2. Create Admin Guide

```bash
cat > docs/keycloak-admin-guide.md << 'EOF'
# Keycloak Administration Guide

## Access Admin Console
- URL: http://localhost:8080/admin
- Username: admin
- Password: Check KEYCLOAK_ADMIN_PASSWORD in .env

## Common Tasks

### Add User to Whitelist
1. Navigate to portfolio realm
2. Authentication → Flows → First Broker Login with Whitelist
3. Click gear icon on email validator
4. Add email to allowed list

### View Active Sessions
1. Navigate to Sessions
2. Shows all active user sessions
3. Can revoke sessions if needed

### Update Google OAuth
1. Identity Providers → Google
2. Update Client ID/Secret as needed

### Export Configuration
\`\`\`bash
docker exec portfolio-keycloak \
  /opt/keycloak/bin/kc.sh export \
  --dir /tmp --realm portfolio
\`\`\`

## Troubleshooting

### User Can't Login
1. Check email in whitelist
2. Verify Google OAuth credentials
3. Check OAuth2-Proxy logs
4. Ensure Redis is running

### Session Issues
1. Check Redis connectivity
2. Verify cookie settings
3. Check token expiration settings
EOF
```

### 3. Create Migration Guide

```bash
cat > docs/auth-migration-guide.md << 'EOF'
# Authentication Migration Guide

## Overview
Migrated from custom Spring Boot auth service to Keycloak + OAuth2-Proxy.

## What Changed
- Auth endpoint: `/validate` → `/oauth2/auth`
- Session storage: Same Redis, different format
- Login URL: `/login` → Keycloak login page
- Email validation: Now in Keycloak

## For Developers

### Environment Variables
New variables needed:
- KEYCLOAK_DB_PASSWORD
- KEYCLOAK_ADMIN_PASSWORD
- OAUTH2_PROXY_CLIENT_SECRET
- OAUTH2_PROXY_COOKIE_SECRET

### Testing Auth Locally
1. Start all services: `docker-compose -f docker-compose.local.yml up -d`
2. Access app at http://localhost
3. Should redirect to Keycloak for login

### Adding New Protected Routes
No changes needed - Caddy forward_auth handles all routes

## For Operations

### Backup
- Keycloak database: PostgreSQL dumps
- Realm config: Export via admin console

### Monitoring
- Keycloak metrics: http://localhost:8080/metrics
- OAuth2-Proxy health: http://localhost:4180/ping
- Session count: Redis key count

### Scaling
- Keycloak: Can run multiple instances
- OAuth2-Proxy: Stateless, scale horizontally
- Redis: Already configured for production
EOF
```

### 4. Update README.md

Add authentication section:

```markdown
## Authentication

This application uses Keycloak for identity management with Google OAuth integration.

### Quick Start

1. Copy `.env.example` to `.env`
2. Add your Google OAuth credentials
3. Run `docker-compose -f docker-compose.local.yml up -d`
4. Access the application at http://localhost

### Configuration

- Allowed users configured via email whitelist in Keycloak
- Sessions stored in Redis with 30-day expiration
- Google OAuth for actual authentication
```

## Validation

- [ ] CLAUDE.md updated with auth info
- [ ] Admin guide created
- [ ] Migration guide created
- [ ] README.md updated
- [ ] All commands tested

## Output

Complete documentation for new authentication system.
