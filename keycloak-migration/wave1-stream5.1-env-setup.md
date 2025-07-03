# Wave 1 - Stream 5.1: Update Environment Files

## Objective

Add all required environment variables for Keycloak and OAuth2-Proxy to support the migration.

## Tasks

### 1. Add to .env file

```bash
# Keycloak Database
KEYCLOAK_DB_PASSWORD=your_secure_password_here
KEYCLOAK_ADMIN_PASSWORD=your_admin_password_here

# OAuth2-Proxy
OAUTH2_PROXY_CLIENT_SECRET=will_be_generated_in_keycloak
OAUTH2_PROXY_COOKIE_SECRET=generate_32_byte_base64_string

# Existing Google OAuth (to be migrated to Keycloak)
# GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET remain unchanged
```

### 2. Generate secure values

```bash
# Generate cookie secret
openssl rand -base64 32

# Generate database password
openssl rand -base64 24
```

### 3. Create .env.example

```bash
# Keycloak Database
KEYCLOAK_DB_PASSWORD=
KEYCLOAK_ADMIN_PASSWORD=

# OAuth2-Proxy
OAUTH2_PROXY_CLIENT_SECRET=
OAUTH2_PROXY_COOKIE_SECRET=

# Google OAuth (existing)
GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=
ALLOWED_EMAILS=
REDIRECT_URI=http://localhost/
```

### 4. Update .gitignore

Ensure these files are ignored:

- `.env`
- `.env.local`
- `keycloak-data/`

## Validation

- [ ] All environment variables added to .env
- [ ] Secure passwords generated
- [ ] .env.example created for team reference
- [ ] .gitignore updated

## Output

Environment files ready for Keycloak deployment.
