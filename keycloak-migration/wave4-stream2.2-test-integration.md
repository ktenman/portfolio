# Wave 4 - Stream 2.2: Test OAuth2-Proxy Integration

## Objective

Thoroughly test OAuth2-Proxy integration with Keycloak and Redis.

## Tasks

### 1. Test Authentication Flow

```bash
# Start all services
docker-compose -f docker-compose.local.yml up -d

# Test redirect to login
curl -v -L http://localhost:4180/
# Should see redirect to Keycloak

# Test with browser
open http://localhost:4180/
# Should see Keycloak login page
```

### 2. Test Session Creation

1. Login via browser with allowed Google account
2. Check Redis for session:

```bash
# List OAuth2-Proxy sessions
docker exec portfolio-redis redis-cli KEYS "*oauth2-proxy*"

# Examine session content
docker exec portfolio-redis redis-cli GET "session_key_here"

# Check session TTL
docker exec portfolio-redis redis-cli TTL "session_key_here"
# Should show ~2592000 seconds (30 days)
```

### 3. Test Forward Auth

```bash
# Simulate Caddy forward_auth request
curl -v http://localhost:4180/oauth2/auth \
  -H "X-Forwarded-Uri: /api/instruments" \
  -H "X-Forwarded-Host: localhost"

# Without session: 401 Unauthorized
# With session (copy cookie from browser):
curl -v http://localhost:4180/oauth2/auth \
  -H "Cookie: _oauth2_proxy=..." \
  -H "X-Forwarded-Uri: /api/instruments"
# Should return 202 Accepted
```

### 4. Test Header Propagation

```bash
# Create test endpoint to echo headers
cat > test-headers.js << 'EOF'
const http = require('http');
http.createServer((req, res) => {
  res.writeHead(200, {'Content-Type': 'application/json'});
  res.end(JSON.stringify(req.headers, null, 2));
}).listen(3000);
EOF

# Run test server
node test-headers.js &

# Configure OAuth2-Proxy upstream
docker exec portfolio-oauth2-proxy sh -c 'echo "upstream=http://host.docker.internal:3000" >> /etc/oauth2-proxy.cfg'
docker restart portfolio-oauth2-proxy

# Test headers
curl http://localhost:4180/ -H "Cookie: _oauth2_proxy=..."
# Should see X-Forwarded-User, X-Forwarded-Email headers
```

### 5. Test Session Refresh

```bash
# Get current session
COOKIE=$(curl -c - http://localhost:4180/ | grep oauth2_proxy | awk '{print $7}')

# Wait 2 hours (or modify cookie_refresh to 1m for testing)
sleep 7200

# Access with old cookie
curl -v http://localhost:4180/ -H "Cookie: _oauth2_proxy=$COOKIE"
# Should refresh session and set new cookie
```

### 6. Test Logout

```bash
# Logout endpoint
curl -v http://localhost:4180/oauth2/sign_out

# Verify session removed from Redis
docker exec portfolio-redis redis-cli KEYS "*oauth2-proxy*"
# Should not see previous session
```

### 7. Performance Test

```bash
# Install apache bench
apt-get install apache2-utils

# Test auth endpoint performance
ab -n 1000 -c 10 -H "Cookie: _oauth2_proxy=..." \
   http://localhost:4180/oauth2/auth

# Check Redis performance
docker exec portfolio-redis redis-cli INFO stats
```

### 8. Error Scenarios

Test these error cases:

- [ ] Blocked email attempts login
- [ ] Keycloak is down
- [ ] Redis is down
- [ ] Invalid/expired session cookie
- [ ] Malformed requests

## Validation Checklist

- [ ] Complete auth flow works
- [ ] Sessions created in Redis
- [ ] Forward auth returns correct status codes
- [ ] Headers properly propagated
- [ ] Session refresh works
- [ ] Logout clears session
- [ ] Performance acceptable
- [ ] Error handling correct

## Output

Full test results documenting OAuth2-Proxy integration.
