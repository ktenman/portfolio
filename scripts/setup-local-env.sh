#!/bin/bash

set -euo pipefail
IFS=$'\n\t'

readonly KEYCLOAK_URL=${KEYCLOAK_URL:-http://keycloak-dev:8080}
readonly KEYCLOAK_ADMIN_USER=${KEYCLOAK_ADMIN_USER:-admin}
readonly KEYCLOAK_ADMIN_PASSWORD=${KEYCLOAK_ADMIN_PASSWORD:-admin}
readonly OAUTH2_PROXY_CLIENT_SECRET=${OAUTH2_PROXY_CLIENT_SECRET:-dev-secret}
readonly MAX_RETRIES=30
readonly RETRY_INTERVAL=5

readonly TEST_USERS=(
  "testuser:${TEST_USER_PASSWORD:-testpass123}:testuser@example.com:Test:User"
  "admin:${ADMIN_PASSWORD:-admin123}:admin@example.com:Admin:User"
  "developer:${DEVELOPER_PASSWORD:-dev123}:developer@example.com:Dev:User"
)

validate_url() {
  local url="$1"
  if [[ ! "$url" =~ ^https?://[a-zA-Z0-9.-]+(:[0-9]+)?(/.*)?$ ]]; then
    echo "Error: Invalid Keycloak URL format: $url" >&2
    exit 1
  fi
}

validate_email() {
  local email="$1"
  if [[ ! "$email" =~ ^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$ ]]; then
    echo "Error: Invalid email format: $email" >&2
    return 1
  fi
  return 0
}

validate_username() {
  local username="$1"
  if [[ ! "$username" =~ ^[a-zA-Z0-9_-]+$ ]]; then
    echo "Error: Invalid username (allowed: alphanumeric, underscore, hyphen): $username" >&2
    return 1
  fi
  return 0
}

check_dependencies() {
  if ! command -v jq >/dev/null 2>&1; then
    echo "Error: jq is required but not installed" >&2
    exit 1
  fi

  if ! command -v curl >/dev/null 2>&1; then
    echo "Error: curl is required but not installed" >&2
    exit 1
  fi
}

validate_url "$KEYCLOAK_URL"
check_dependencies

echo "Setting up local development environment..."

echo "Waiting for Keycloak to be ready..."
retry_count=0
until curl -fsSL "$KEYCLOAK_URL/health/ready" > /dev/null 2>&1; do
  retry_count=$((retry_count + 1))
  if [ $retry_count -ge $MAX_RETRIES ]; then
    echo "Error: Keycloak not ready after $MAX_RETRIES attempts" >&2
    exit 1
  fi
  echo "Keycloak not ready yet, waiting... (attempt $retry_count/$MAX_RETRIES)"
  sleep "$RETRY_INTERVAL"
done

echo "Keycloak is ready. Getting admin token..."
TOKEN_RESPONSE=$(curl -fsSL -X POST "$KEYCLOAK_URL/realms/master/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "username=$KEYCLOAK_ADMIN_USER" \
  --data-urlencode "password=$KEYCLOAK_ADMIN_PASSWORD" \
  -d "grant_type=password" \
  -d "client_id=admin-cli" 2>&1)

TOKEN=$(echo "$TOKEN_RESPONSE" | jq -r '.access_token // empty' 2>/dev/null)

if [ -z "$TOKEN" ]; then
  echo "Error: Failed to get admin token" >&2
  echo "Response: $TOKEN_RESPONSE" >&2
  exit 1
fi

check_client_exists() {
  local client_id="$1"
  local response

  response=$(curl -fsSL "$KEYCLOAK_URL/admin/realms/portfolio/clients" \
    -H "Authorization: Bearer $TOKEN" 2>/dev/null)

  echo "$response" | jq -r --arg clientId "$client_id" \
    '.[] | select(.clientId==$clientId) | .clientId' 2>/dev/null
}

CLIENT_EXISTS=$(check_client_exists "portfolio-oauth2-proxy")

CLIENT_CONFIG=$(jq -n \
  --arg secret "$OAUTH2_PROXY_CLIENT_SECRET" \
  '{
    "clientId": "portfolio-oauth2-proxy",
    "name": "Portfolio OAuth2 Proxy",
    "enabled": true,
    "clientAuthenticatorType": "client-secret",
    "secret": $secret,
    "redirectUris": [
      "http://localhost:4180/oauth2/callback",
      "http://localhost:61234/oauth2/callback",
      "http://localhost/oauth2/callback"
    ],
    "webOrigins": [
      "http://localhost:4180",
      "http://localhost:61234",
      "http://localhost"
    ],
    "protocol": "openid-connect",
    "standardFlowEnabled": true,
    "directAccessGrantsEnabled": false,
    "publicClient": false,
    "frontchannelLogout": true,
    "attributes": {
      "pkce.code.challenge.method": "S256"
    }
  }')

if [ "$CLIENT_EXISTS" = "portfolio-oauth2-proxy" ]; then
  echo "OAuth2-Proxy client already exists, updating..."

  EXISTING_CLIENT_ID=$(curl -fsSL "$KEYCLOAK_URL/admin/realms/portfolio/clients" \
    -H "Authorization: Bearer $TOKEN" | \
    jq -r '.[] | select(.clientId=="portfolio-oauth2-proxy") | .id' 2>/dev/null)

  if [ -n "$EXISTING_CLIENT_ID" ] && [ "$EXISTING_CLIENT_ID" != "null" ]; then
    curl -fsSL -X PUT "$KEYCLOAK_URL/admin/realms/portfolio/clients/$EXISTING_CLIENT_ID" \
      -H "Authorization: Bearer $TOKEN" \
      -H "Content-Type: application/json" \
      -d "$CLIENT_CONFIG" > /dev/null
  fi
else
  echo "Creating OAuth2-Proxy client..."
  curl -fsSL -X POST "$KEYCLOAK_URL/admin/realms/portfolio/clients" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "$CLIENT_CONFIG" > /dev/null
fi

echo "Configuring realm for local development..."

REALM_CONFIG=$(jq -n '{
  "registrationAllowed": true,
  "loginWithEmailAllowed": true,
  "duplicateEmailsAllowed": false,
  "verifyEmail": false
}')

curl -fsSL -X PUT "$KEYCLOAK_URL/admin/realms/portfolio" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "$REALM_CONFIG" > /dev/null

create_user() {
  local user_data="$1"
  local username email firstname lastname password

  IFS=':' read -r username password email firstname lastname <<< "$user_data"

  if ! validate_username "$username" || ! validate_email "$email"; then
    echo "Warning: Skipping invalid user: $username" >&2
    return 1
  fi

  local user_json
  user_json=$(jq -n \
    --arg username "$username" \
    --arg email "$email" \
    --arg firstname "$firstname" \
    --arg lastname "$lastname" \
    --arg password "$password" \
    '{
      "username": $username,
      "email": $email,
      "firstName": $firstname,
      "lastName": $lastname,
      "enabled": true,
      "emailVerified": true,
      "credentials": [{
        "type": "password",
        "value": $password,
        "temporary": false
      }]
    }')

  if curl -fsSL -X POST "$KEYCLOAK_URL/admin/realms/portfolio/users" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "$user_json" > /dev/null 2>&1; then

    if [ -f /app/emails.txt ]; then
      if ! grep -qF "$email" /app/emails.txt 2>/dev/null; then
        echo "$email" >> /app/emails.txt
      fi
    fi
  fi
}

echo "Creating test users..."
for user_data in "${TEST_USERS[@]}"; do
  create_user "$user_data" &
done

wait

echo ""
echo "Local environment setup complete!"
echo ""
echo "Test user credentials:"
for user_data in "${TEST_USERS[@]}"; do
  IFS=':' read -r username password email _ _ <<< "$user_data"
  printf "  %-15s | Email: %-25s\n" "$username" "$email"
done
echo ""
echo "Note: Passwords are stored in environment variables for security"
echo "      Default passwords are for development only - DO NOT use in production!"
