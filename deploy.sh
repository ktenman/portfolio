#!/bin/bash
set -e

# Generate encryption key if not present
if ! grep -q "KEYCLOAK_ENCRYPTION_KEY" .env 2>/dev/null; then
  ENCRYPTION_KEY=$(openssl rand -base64 32 | tr -dc 'a-zA-Z0-9' | fold -w 32 | head -n 1)
  echo "KEYCLOAK_ENCRYPTION_KEY=$ENCRYPTION_KEY" >> .env
  echo "Generated encryption key"
fi

# Run Keycloak setup
./setup-keycloak.sh

# Start all services
docker-compose down
docker-compose build keycloak
docker-compose up -d

echo "Waiting for Keycloak initializer to complete..."
docker-compose logs -f keycloak-initializer
