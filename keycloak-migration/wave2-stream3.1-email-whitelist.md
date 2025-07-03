# Wave 2 - Stream 3.1: Install Email Whitelist Extension

## Objective

Install and configure email whitelist functionality in Keycloak.

## Tasks

### 1. Download Extension

```bash
# Create extensions directory
mkdir -p keycloak-extensions

# Option A: Use pre-built extension
cd keycloak-extensions
wget https://github.com/micedre/keycloak-mail-whitelisting/releases/latest/download/keycloak-mail-whitelisting.jar

# Option B: Build from source
git clone https://github.com/micedre/keycloak-mail-whitelisting.git
cd keycloak-mail-whitelisting
mvn clean package
cp target/keycloak-mail-whitelisting-*.jar ../keycloak-extensions/
```

### 2. Update docker-compose.local.yml

```yaml
keycloak:
  # ... existing config ...
  volumes:
    - ./keycloak-extensions:/opt/keycloak/providers
  # ... rest of config ...
```

### 3. Restart Keycloak with extension

```bash
# Rebuild to include extension
docker-compose -f docker-compose.local.yml down keycloak
docker-compose -f docker-compose.local.yml up -d keycloak

# Verify extension loaded
docker logs portfolio-keycloak 2>&1 | grep -i "mail"
```

### 4. Configure Authentication Flow

1. Login to Keycloak admin console
2. Select `portfolio` realm
3. Navigate to Authentication → Flows
4. Select "First Broker Login" flow
5. Click "Add execution" after "Review Profile"
6. Select "Profile Validation with domain block" or "Profile Validation With Email Domain Check"
7. Set to "REQUIRED"
8. Click gear icon → Config:
   - For domain whitelist: `gmail.com,company.com`
   - For specific emails: Use comma-separated list

### 5. Update Google IdP to use new flow

1. Navigate to Identity Providers → Google
2. Under "First Login Flow" select your modified flow
3. Save

### 6. Create fallback email list (optional)

```bash
# Create allowed emails file
cat > keycloak-config/allowed-emails.txt << EOF
user1@example.com
user2@example.com
${ALLOWED_EMAILS}
EOF

# Mount in docker-compose if needed
volumes:
  - ./keycloak-config/allowed-emails.txt:/opt/keycloak/data/allowed-emails.txt
```

## Validation

- [ ] Extension JAR in providers directory
- [ ] Keycloak recognizes extension
- [ ] Authentication flow configured
- [ ] Email validation execution added
- [ ] Test with allowed email (success)
- [ ] Test with non-allowed email (blocked)

## Output

Email whitelist functionality active in Keycloak.
