#!/bin/bash

set -euo pipefail
IFS=$'\n\t'

readonly KEYCLOAK_URL=${KEYCLOAK_URL:-http://localhost:8080}
readonly KEYCLOAK_ADMIN_USER=${KEYCLOAK_ADMIN_USER:-admin}
readonly KEYCLOAK_ADMIN_PASSWORD=${KEYCLOAK_ADMIN_PASSWORD:-}
readonly TEST_USERNAME=${TEST_USERNAME:-testuser}
readonly TEST_EMAIL=${TEST_EMAIL:-testuser@example.com}
readonly TEST_PASSWORD=${TEST_PASSWORD:-}
readonly MAX_RETRIES=30
readonly RETRY_INTERVAL=5

check_required_env() {
  local missing=()

  [[ -z "${KEYCLOAK_ADMIN_PASSWORD}" ]] && missing+=("KEYCLOAK_ADMIN_PASSWORD")
  [[ -z "${TEST_PASSWORD}" ]] && missing+=("TEST_PASSWORD")

  if [ ${#missing[@]} -gt 0 ]; then
    echo "Error: Missing required environment variables: ${missing[*]}" >&2
    echo "" >&2
    echo "Usage:" >&2
    echo "  KEYCLOAK_ADMIN_PASSWORD=<admin-password> TEST_PASSWORD=<test-password> $0" >&2
    echo "" >&2
    echo "Optional environment variables:" >&2
    echo "  KEYCLOAK_URL (default: http://localhost:8080)" >&2
    echo "  KEYCLOAK_ADMIN_USER (default: admin)" >&2
    echo "  TEST_USERNAME (default: testuser)" >&2
    echo "  TEST_EMAIL (default: testuser@example.com)" >&2
    exit 1
  fi
}

validate_email() {
  local email="$1"
  if [[ ! "$email" =~ ^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$ ]]; then
    echo "Error: Invalid email format: $email" >&2
    exit 1
  fi
}

validate_username() {
  local username="$1"
  if [[ ! "$username" =~ ^[a-zA-Z0-9_-]+$ ]]; then
    echo "Error: Invalid username (allowed: alphanumeric, underscore, hyphen): $username" >&2
    exit 1
  fi
}

check_required_env
validate_email "$TEST_EMAIL"
validate_username "$TEST_USERNAME"

if ! command -v jq >/dev/null 2>&1; then
    echo "Error: jq is required but not installed" >&2
    exit 1
fi

echo "Creating local test user in Keycloak..."

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

echo "Getting admin token..."
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

echo "Creating test user..."
USER_DATA=$(jq -n \
  --arg username "$TEST_USERNAME" \
  --arg email "$TEST_EMAIL" \
  --arg password "$TEST_PASSWORD" \
  '{
    "username": $username,
    "email": $email,
    "firstName": "Test",
    "lastName": "User",
    "enabled": true,
    "emailVerified": true,
    "credentials": [{
      "type": "password",
      "value": $password,
      "temporary": false
    }]
  }')

if ! curl -fsSL -X POST "$KEYCLOAK_URL/admin/realms/portfolio/users" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "$USER_DATA"; then
  echo "Error: Failed to create test user" >&2
  exit 1
fi

echo ""
echo "Test user created successfully!"
echo "Username: $TEST_USERNAME"
echo "Email: $TEST_EMAIL"
echo "Note: Password is stored securely and not displayed"