#!/bin/bash

echo "Creating local test user in Keycloak..."

# Wait for Keycloak to be ready
echo "Waiting for Keycloak to be ready..."
until curl -s http://localhost:8080/health/ready > /dev/null; do
  echo "Keycloak not ready yet, waiting..."
  sleep 5
done

echo "Getting admin token..."
TOKEN=$(curl -s -X POST http://localhost:8080/realms/master/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=admin" \
  -d "password=admin" \
  -d "grant_type=password" \
  -d "client_id=admin-cli" | jq -r '.access_token')

if [ "$TOKEN" = "null" ] || [ -z "$TOKEN" ]; then
  echo "Failed to get admin token"
  exit 1
fi

# Create a test user
echo "Creating test user..."
curl -s -X POST http://localhost:8080/admin/realms/portfolio/users \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "email": "testuser@example.com",
    "firstName": "Test",
    "lastName": "User",
    "enabled": true,
    "emailVerified": true,
    "credentials": [{
      "type": "password",
      "value": "testpass123",
      "temporary": false
    }]
  }'

echo "Test user created!"
echo "Username: testuser"
echo "Password: testpass123"
echo "Email: testuser@example.com"