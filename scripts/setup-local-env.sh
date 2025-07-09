#!/bin/bash

echo "Setting up local development environment..."

# Wait for Keycloak to be ready
echo "Waiting for Keycloak to be ready..."
until curl -s http://keycloak-dev:8080/health/ready > /dev/null; do
  echo "Keycloak not ready yet, waiting..."
  sleep 5
done

echo "Keycloak is ready. Getting admin token..."
TOKEN=$(curl -s -X POST http://keycloak-dev:8080/realms/master/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=admin" \
  -d "password=admin" \
  -d "grant_type=password" \
  -d "client_id=admin-cli" | jq -r '.access_token')

if [ "$TOKEN" = "null" ] || [ -z "$TOKEN" ]; then
  echo "Failed to get admin token"
  exit 1
fi

# Check if client already exists
CLIENT_EXISTS=$(curl -s http://keycloak-dev:8080/admin/realms/portfolio/clients \
  -H "Authorization: Bearer $TOKEN" | jq -r '.[] | select(.clientId=="portfolio-oauth2-proxy") | .clientId')

if [ "$CLIENT_EXISTS" = "portfolio-oauth2-proxy" ]; then
  echo "OAuth2-Proxy client already exists, updating..."
  CLIENT_ID=$(curl -s http://keycloak-dev:8080/admin/realms/portfolio/clients \
    -H "Authorization: Bearer $TOKEN" | jq -r '.[] | select(.clientId=="portfolio-oauth2-proxy") | .id')
  
  curl -s -X PUT http://keycloak-dev:8080/admin/realms/portfolio/clients/$CLIENT_ID \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{
      "clientId": "portfolio-oauth2-proxy",
      "name": "Portfolio OAuth2 Proxy",
      "enabled": true,
      "clientAuthenticatorType": "client-secret",
      "secret": "dev-secret",
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
    }'
else
  echo "Creating OAuth2-Proxy client..."
  curl -s -X POST http://keycloak-dev:8080/admin/realms/portfolio/clients \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{
      "clientId": "portfolio-oauth2-proxy",
      "name": "Portfolio OAuth2 Proxy",
      "enabled": true,
      "clientAuthenticatorType": "client-secret",
      "secret": "dev-secret",
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
    }'
fi

echo "Configuring realm for local development..."

# Function to create a user
create_user() {
  local username=$1
  local password=$2
  local email=$3
  local firstname=$4
  local lastname=$5
  
  curl -s -X POST http://keycloak-dev:8080/admin/realms/portfolio/users \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "{
      \"username\": \"$username\",
      \"email\": \"$email\",
      \"firstName\": \"$firstname\",
      \"lastName\": \"$lastname\",
      \"enabled\": true,
      \"emailVerified\": true,
      \"credentials\": [{
        \"type\": \"password\",
        \"value\": \"$password\",
        \"temporary\": false
      }]
    }" > /dev/null 2>&1
  
  # Add email to allowed emails file if it exists
  if [ -f /app/emails.txt ]; then
    if ! grep -q "$email" /app/emails.txt; then
      echo "$email" >> /app/emails.txt
    fi
  fi
}

# Enable direct access grants for local testing
curl -s -X PUT http://keycloak-dev:8080/admin/realms/portfolio \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "registrationAllowed": true,
    "loginWithEmailAllowed": true,
    "duplicateEmailsAllowed": false,
    "verifyEmail": false
  }' &

# Create test users in parallel
echo "Creating test users..."
create_user "testuser" "testpass123" "testuser@example.com" "Test" "User" &
create_user "admin" "admin123" "admin@example.com" "Admin" "User" &
create_user "developer" "dev123" "developer@example.com" "Dev" "User" &

# Wait for all background jobs to complete
wait

echo "Local environment setup complete!"
echo ""
echo "Test user credentials:"
echo "1. Username: testuser   | Password: testpass123"
echo "2. Username: admin      | Password: admin123"  
echo "3. Username: developer  | Password: dev123"