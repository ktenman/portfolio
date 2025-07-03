# Wave 3 - Stream 2.1: Deploy OAuth2-Proxy

## Objective

Deploy and configure OAuth2-Proxy to work with Keycloak and Redis.

## Tasks

### 1. Add OAuth2-Proxy to docker-compose.local.yml

```yaml
oauth2-proxy:
  image: quay.io/oauth2-proxy/oauth2-proxy:v7.6.0
  container_name: portfolio-oauth2-proxy
  environment:
    # Provider settings
    OAUTH2_PROXY_PROVIDER: oidc
    OAUTH2_PROXY_OIDC_ISSUER_URL: http://keycloak:8080/realms/portfolio
    OAUTH2_PROXY_CLIENT_ID: portfolio-oauth2-proxy
    OAUTH2_PROXY_CLIENT_SECRET: ${OAUTH2_PROXY_CLIENT_SECRET}
    OAUTH2_PROXY_CODE_CHALLENGE_METHOD: S256

    # Cookie settings
    OAUTH2_PROXY_COOKIE_SECRET: ${OAUTH2_PROXY_COOKIE_SECRET}
    OAUTH2_PROXY_COOKIE_EXPIRE: 720h
    OAUTH2_PROXY_COOKIE_REFRESH: 1h
    OAUTH2_PROXY_COOKIE_NAME: _oauth2_proxy
    OAUTH2_PROXY_COOKIE_HTTPONLY: true

    # Session storage
    OAUTH2_PROXY_SESSION_STORE_TYPE: redis
    OAUTH2_PROXY_REDIS_CONNECTION_URL: redis://redis:6379
    OAUTH2_PROXY_REDIS_USE_SENTINEL: false

    # URLs
    OAUTH2_PROXY_HTTP_ADDRESS: 0.0.0.0:4180
    OAUTH2_PROXY_REDIRECT_URL: http://localhost/oauth2/callback
    OAUTH2_PROXY_WHITELIST_DOMAINS: localhost

    # Features
    OAUTH2_PROXY_EMAIL_DOMAINS: '*'
    OAUTH2_PROXY_PASS_ACCESS_TOKEN: true
    OAUTH2_PROXY_PASS_AUTHORIZATION_HEADER: true
    OAUTH2_PROXY_SET_XAUTHREQUEST: true
    OAUTH2_PROXY_SET_AUTHORIZATION_HEADER: true
    OAUTH2_PROXY_SKIP_PROVIDER_BUTTON: true

    # Logging
    OAUTH2_PROXY_STANDARD_LOGGING: true
    OAUTH2_PROXY_REQUEST_LOGGING: true
    OAUTH2_PROXY_AUTH_LOGGING: true
  ports:
    - '4180:4180'
  depends_on:
    - redis
    - keycloak
  networks:
    - portfolio-network
  healthcheck:
    test: ['CMD', 'wget', '--spider', '-q', 'http://localhost:4180/ping']
    interval: 30s
    timeout: 10s
    retries: 3
```

### 2. Create OAuth2-Proxy configuration file (alternative to env vars)

```bash
cat > oauth2-proxy.cfg << 'EOF'
# Provider config
provider = "oidc"
oidc_issuer_url = "http://keycloak:8080/realms/portfolio"
client_id = "portfolio-oauth2-proxy"
client_secret = "${OAUTH2_PROXY_CLIENT_SECRET}"
code_challenge_method = "S256"

# Cookie config
cookie_secret = "${OAUTH2_PROXY_COOKIE_SECRET}"
cookie_expire = "720h"
cookie_refresh = "1h"
cookie_httponly = true
cookie_secure = false  # Set to true in production

# Redis session store
session_store_type = "redis"
redis_connection_url = "redis://redis:6379"

# Server config
http_address = "0.0.0.0:4180"
redirect_url = "http://localhost/oauth2/callback"
upstreams = ["http://backend:8081/"]

# Security
email_domains = ["*"]
skip_provider_button = true
pass_access_token = true
set_xauthrequest = true
EOF
```

### 3. Start OAuth2-Proxy

```bash
# Start the service
docker-compose -f docker-compose.local.yml up -d oauth2-proxy

# Check logs
docker-compose -f docker-compose.local.yml logs -f oauth2-proxy

# Look for: "HTTP: listening on 0.0.0.0:4180"
```

### 4. Test OAuth2-Proxy endpoints

```bash
# Health check
curl http://localhost:4180/ping

# Should redirect to Keycloak
curl -v http://localhost:4180/

# Auth endpoint (for Caddy forward_auth)
curl -v http://localhost:4180/oauth2/auth
```

### 5. Verify Redis integration

```bash
# Check Redis for sessions
docker exec portfolio-redis redis-cli KEYS "*oauth2*"

# Monitor Redis activity
docker exec portfolio-redis redis-cli MONITOR
# Then try to authenticate via browser
```

## Validation

- [ ] OAuth2-Proxy starts successfully
- [ ] Health endpoint responds
- [ ] Redirects to Keycloak for auth
- [ ] Sessions created in Redis
- [ ] Can complete OAuth flow

## Output

OAuth2-Proxy deployed and integrated with Keycloak and Redis.
