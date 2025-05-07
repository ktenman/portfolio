#!/bin/bash
set -e

# Generate a random client secret if not already exists
if ! grep -q "KEYCLOAK_CLIENT_SECRET" .env 2>/dev/null; then
  CLIENT_SECRET=$(openssl rand -base64 32 | tr -dc 'a-zA-Z0-9' | fold -w 32 | head -n 1)
  echo "Generated client secret: $CLIENT_SECRET"
  echo "KEYCLOAK_CLIENT_SECRET=$CLIENT_SECRET" >> .env
else
  echo "KEYCLOAK_CLIENT_SECRET already exists in .env"
fi

# Generate encryption key if not already exists
if ! grep -q "KEYCLOAK_ENCRYPTION_KEY" .env 2>/dev/null; then
  ENCRYPTION_KEY=$(openssl rand -base64 32 | tr -dc 'a-zA-Z0-9' | fold -w 32 | head -n 1)
  echo "Generated encryption key: $ENCRYPTION_KEY"
  echo "KEYCLOAK_ENCRYPTION_KEY=$ENCRYPTION_KEY" >> .env
else
  echo "KEYCLOAK_ENCRYPTION_KEY already exists in .env"
fi

echo "Keycloak environment setup complete"
