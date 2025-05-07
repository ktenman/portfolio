#!/bin/sh
set -e

echo "Waiting for Keycloak to be ready..."
until curl -s --fail http://keycloak:8080/health/ready; do
  sleep 5
done

echo "Authenticating to Keycloak..."
TOKEN=$(curl -s -X POST http://keycloak:8080/realms/master/protocol/openid-connect/token \
  -d "grant_type=password" \
  -d "client_id=admin-cli" \
  -d "username=${KEYCLOAK_ADMIN}" \
  -d "password=${KEYCLOAK_ADMIN_PASSWORD}" | jq -r '.access_token')

if [ -z "$TOKEN" ] || [ "$TOKEN" = "null" ]; then
  echo "Failed to authenticate with Keycloak"
  exit 1
fi

echo "Creating portfolio realm..."
REALM_EXISTS=$(curl -s -o /dev/null -w "%{http_code}" -H "Authorization: Bearer $TOKEN" http://keycloak:8080/admin/realms/portfolio)

if [ "$REALM_EXISTS" != "200" ]; then
  curl -s -X POST http://keycloak:8080/admin/realms \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"realm":"portfolio","enabled":true,"displayName":"Portfolio Application"}'

  echo "Portfolio realm created successfully"
else
  echo "Portfolio realm already exists"
fi

# Create client
CLIENT_EXISTS=$(curl -s -H "Authorization: Bearer $TOKEN" http://keycloak:8080/admin/realms/portfolio/clients | jq '.[] | select(.clientId=="portfolio-app") | .id')

if [ -z "$CLIENT_EXISTS" ] || [ "$CLIENT_EXISTS" = "null" ]; then
  CLIENT_ID=$(curl -s -X POST http://keycloak:8080/admin/realms/portfolio/clients \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"clientId":"portfolio-app","enabled":true,"publicClient":false,"secret":"'${CLIENT_SECRET}'","redirectUris":["https://fov.ee/*"],"webOrigins":["https://fov.ee"]}' \
    -v 2>&1 | grep Location | sed 's/.*\/\([^\/]*\)$/\1/' | tr -d '[:space:]')

  echo "Client created with ID: $CLIENT_ID"
else
  echo "Portfolio-app client already exists"
fi

# Create user role
ROLE_EXISTS=$(curl -s -H "Authorization: Bearer $TOKEN" http://keycloak:8080/admin/realms/portfolio/roles | jq '.[] | select(.name=="user") | .name')

if [ -z "$ROLE_EXISTS" ] || [ "$ROLE_EXISTS" = "null" ]; then
  curl -s -X POST http://keycloak:8080/admin/realms/portfolio/roles \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"name":"user","description":"Regular user role"}'

  echo "User role created successfully"
else
  echo "User role already exists"
fi

# Create Google identity provider
IDP_EXISTS=$(curl -s -o /dev/null -w "%{http_code}" -H "Authorization: Bearer $TOKEN" http://keycloak:8080/admin/realms/portfolio/identity-provider/instances/google)

if [ "$IDP_EXISTS" != "200" ]; then
  curl -s -X POST http://keycloak:8080/admin/realms/portfolio/identity-provider/instances \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"alias":"google","providerId":"google","enabled":true,"config":{"clientId":"'${GOOGLE_CLIENT_ID}'","clientSecret":"'${GOOGLE_CLIENT_SECRET}'","useJwksUrl":"true"}}'

  echo "Google identity provider created successfully"
else
  echo "Google identity provider already exists"
fi

echo "Keycloak initialization completed successfully"
