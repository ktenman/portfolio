# Wave 3 - Stream 3.2: Alternative Script Authenticator

## Objective

Create JavaScript-based email whitelist authenticator as backup option.

## Tasks

### 1. Enable Script Authenticator

1. Login to Keycloak admin console
2. Navigate to Realm Settings → Providers
3. If "script" authenticator not visible, may need to enable:
   ```bash
   # Add to docker-compose keycloak environment
   KC_FEATURES: scripts
   KC_FEATURES_SCRIPTS: enabled
   ```

### 2. Create Custom Authentication Flow

1. Navigate to Authentication → Flows
2. Click "Create flow"
   - Name: `Browser with Email Whitelist`
   - Description: `Browser flow with email validation`
   - Top Level Flow Type: `generic`
3. Add executions in order:
   - Cookie (Alternative)
   - Identity Provider Redirector (Alternative)
   - Forms (Alternative)
     - Username Password Form (Required)
   - Script (Required) - Our email validator

### 3. Create Email Whitelist Script

1. Click gear icon next to Script execution → Config
2. Add configuration:

   ```javascript
   // Script Name: email-whitelist-validator

   function authenticate(context) {
     var username = context.getAuthenticationSession().getAuthenticatedUser().getEmail()
     var AuthenticationFlowError = Java.type('org.keycloak.authentication.AuthenticationFlowError')

     // Get allowed emails from realm attributes or hardcode
     var allowedEmails = [
       'user1@example.com',
       'user2@company.com',
       // Add emails from ALLOWED_EMAILS env var
     ]

     // Alternative: Get from realm attribute
     // var allowedList = realm.getAttribute("allowed_emails");
     // var allowedEmails = allowedList ? allowedList.split(",") : [];

     LOG.info('Checking email: ' + username)

     if (allowedEmails.indexOf(username) !== -1) {
       LOG.info('Email ' + username + ' is allowed')
       context.success()
     } else {
       LOG.warn('Email ' + username + ' is not in whitelist')
       context.failure(AuthenticationFlowError.INVALID_USER)
     }
   }
   ```

### 4. Configure for Google IdP

1. Duplicate "First Broker Login" flow
2. Name: `First Broker Login with Whitelist`
3. Add Script execution after "Create User If Unique"
4. Use same script configuration

### 5. Apply flows

1. Navigate to Authentication → Bindings
2. Browser Flow: `Browser with Email Whitelist`
3. Navigate to Identity Providers → Google
4. First Login Flow: `First Broker Login with Whitelist`

### 6. Store whitelist in realm attributes

```bash
# Via Admin REST API
TOKEN=$(curl -s -X POST "http://localhost:8080/realms/master/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=admin" \
  -d "password=${KEYCLOAK_ADMIN_PASSWORD}" \
  -d "grant_type=password" \
  -d "client_id=admin-cli" | jq -r '.access_token')

# Add realm attribute
curl -X PUT "http://localhost:8080/admin/realms/portfolio" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "attributes": {
      "allowed_emails": "user1@example.com,user2@example.com"
    }
  }'
```

## Validation

- [ ] Script authenticator available
- [ ] Custom flow created
- [ ] Script validates emails correctly
- [ ] Test allowed email (passes)
- [ ] Test blocked email (fails)
- [ ] Realm attributes storing whitelist

## Output

Backup email validation method configured and tested.
