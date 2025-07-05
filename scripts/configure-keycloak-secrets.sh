#!/bin/bash

set -e

KEYCLOAK_URL=${KEYCLOAK_URL:-http://localhost:8080}
REALM_NAME=${REALM_NAME:-portfolio}
MAX_RETRIES=30
RETRY_INTERVAL=5

echo "Configuring Keycloak secrets..."
echo "Keycloak URL: $KEYCLOAK_URL"
echo "Realm: $REALM_NAME"

retry_count=0
until /opt/keycloak/bin/kcadm.sh config credentials \
    --server "$KEYCLOAK_URL" \
    --realm master \
    --user admin \
    --password "$KEYCLOAK_ADMIN_PASSWORD" >/dev/null 2>&1; do
    
    retry_count=$((retry_count + 1))
    if [ $retry_count -ge $MAX_RETRIES ]; then
        echo "Failed to connect to Keycloak after $MAX_RETRIES attempts"
        exit 1
    fi
    
    echo "Waiting for Keycloak to be ready... (attempt $retry_count/$MAX_RETRIES)"
    sleep $RETRY_INTERVAL
done

echo "Successfully authenticated with Keycloak"

echo "Updating Google OAuth client secret..."
/opt/keycloak/bin/kcadm.sh update identity-provider/instances/google \
    -r "$REALM_NAME" \
    -s config.clientSecret="$GOOGLE_CLIENT_SECRET" || {
    echo "Failed to update Google OAuth client secret"
    exit 1
}

echo "Finding OAuth2-Proxy client ID..."
CLIENT_ID=$(/opt/keycloak/bin/kcadm.sh get clients -r "$REALM_NAME" --fields id,clientId | \
    grep -B1 '"portfolio-oauth2-proxy"' | grep '"id"' | cut -d'"' -f4)

if [ -z "$CLIENT_ID" ]; then
    echo "OAuth2-Proxy client not found"
    exit 1
fi

echo "Updating OAuth2-Proxy client secret..."
/opt/keycloak/bin/kcadm.sh update "clients/$CLIENT_ID" \
    -r "$REALM_NAME" \
    -s secret="$OAUTH2_PROXY_CLIENT_SECRET" || {
    echo "Failed to update OAuth2-Proxy client secret"
    exit 1
}

echo "Keycloak secrets configured successfully!"