# Wave 2 - Stream 1.3: Configure Keycloak Realm

## Objective

Configure Keycloak realm with Google Identity Provider and client settings.

## Tasks

### 1. Create Portfolio Realm

1. Login to admin console: http://localhost:8080/admin
2. Click dropdown (top-left) → "Create Realm"
3. Realm name: `portfolio`
4. Click "Create"

### 2. Configure Realm Settings

Navigate to Realm Settings → Tokens:

- SSO Session Idle: 30 days
- SSO Session Max: 30 days
- SSO Session Idle Remember Me: 30 days
- SSO Session Max Remember Me: 30 days

### 3. Setup Google Identity Provider

1. Navigate to Identity Providers → Add provider → Google
2. Configure:
   - Client ID: `${GOOGLE_CLIENT_ID}` (from .env)
   - Client Secret: `${GOOGLE_CLIENT_SECRET}` (from .env)
   - Default Scopes: `openid email profile`
   - Store Tokens: ON
   - Trust Email: ON
   - First Login Flow: `first broker login`

### 4. Create OIDC Client for OAuth2-Proxy

1. Navigate to Clients → Create client
2. General Settings:
   - Client type: `OpenID Connect`
   - Client ID: `portfolio-oauth2-proxy`
3. Capability config:
   - Client authentication: ON
   - Authorization: OFF
   - Authentication flow: Standard flow only
4. Login settings:
   - Valid redirect URIs:
     - `http://localhost/oauth2/callback`
     - `http://localhost:4180/oauth2/callback`
   - Valid post logout redirect URIs: `http://localhost/`
   - Web origins: `http://localhost`

### 5. Get Client Secret

1. Navigate to Clients → `portfolio-oauth2-proxy` → Credentials
2. Copy the Client secret
3. Update .env: `OAUTH2_PROXY_CLIENT_SECRET=<copied-secret>`

### 6. Create Mappers

Navigate to Clients → `portfolio-oauth2-proxy` → Client scopes → Dedicated scopes → Add mapper:

1. Email mapper:
   - Name: `email`
   - Mapper Type: `User Property`
   - Property: `email`
   - Token Claim Name: `email`
   - Add to ID token: ON

2. Groups mapper (optional):
   - Name: `groups`
   - Mapper Type: `Group Membership`
   - Token Claim Name: `groups`
   - Add to ID token: ON

### 7. Export configuration

```bash
# Export realm config for backup
docker exec portfolio-keycloak \
  /opt/keycloak/bin/kc.sh export \
  --dir /tmp \
  --realm portfolio

docker cp portfolio-keycloak:/tmp/portfolio-realm.json ./keycloak-config/
```

## Validation

- [ ] Portfolio realm created
- [ ] Google IdP configured
- [ ] OIDC client created
- [ ] Client secret obtained and saved
- [ ] Token lifetimes set to 30 days
- [ ] Configuration exported

## Output

Keycloak realm fully configured and ready for OAuth2-Proxy.
