#!/bin/bash

set -euo pipefail
IFS=$'\n\t'

readonly KEYCLOAK_URL=${KEYCLOAK_URL:-http://localhost:8080}
readonly REALM_NAME=${REALM_NAME:-portfolio}
readonly MAX_RETRIES=30
readonly RETRY_INTERVAL=5

validate_url() {
  local url="$1"
  if [[ ! "$url" =~ ^https?://[a-zA-Z0-9.-]+(:[0-9]+)?(/.*)?$ ]]; then
    echo "Error: Invalid Keycloak URL format: $url" >&2
    exit 1
  fi
}

validate_realm() {
  local realm="$1"
  if [[ ! "$realm" =~ ^[a-zA-Z0-9_-]+$ ]]; then
    echo "Error: Invalid realm name (allowed: alphanumeric, underscore, hyphen): $realm" >&2
    exit 1
  fi
}

check_required_env() {
  local missing=()

  [[ -z "${KEYCLOAK_ADMIN_PASSWORD:-}" ]] && missing+=("KEYCLOAK_ADMIN_PASSWORD")
  [[ -z "${GOOGLE_CLIENT_SECRET:-}" ]] && missing+=("GOOGLE_CLIENT_SECRET")
  [[ -z "${OAUTH2_PROXY_CLIENT_SECRET:-}" ]] && missing+=("OAUTH2_PROXY_CLIENT_SECRET")

  if [ ${#missing[@]} -gt 0 ]; then
    echo "Error: Missing required environment variables: ${missing[*]}" >&2
    echo "Please set these variables before running this script" >&2
    exit 1
  fi
}

validate_url "$KEYCLOAK_URL"
validate_realm "$REALM_NAME"
check_required_env

echo "Configuring Keycloak secrets..."
echo "Keycloak URL: $KEYCLOAK_URL"
echo "Realm: $REALM_NAME"

retry_count=0
until echo "$KEYCLOAK_ADMIN_PASSWORD" | /opt/keycloak/bin/kcadm.sh config credentials \
    --server "$KEYCLOAK_URL" \
    --realm master \
    --user admin \
    --password-stdin >/dev/null 2>&1; do

    retry_count=$((retry_count + 1))
    if [ $retry_count -ge $MAX_RETRIES ]; then
        echo "Failed to connect to Keycloak after $MAX_RETRIES attempts" >&2
        exit 1
    fi

    echo "Waiting for Keycloak to be ready... (attempt $retry_count/$MAX_RETRIES)"
    sleep "$RETRY_INTERVAL"
done

echo "Successfully authenticated with Keycloak"

echo "Updating Google OAuth client secret..."
if ! /opt/keycloak/bin/kcadm.sh update identity-provider/instances/google \
    -r "$REALM_NAME" \
    -s config.clientSecret="$GOOGLE_CLIENT_SECRET"; then
    echo "Error: Failed to update Google OAuth client secret" >&2
    exit 1
fi

echo "Finding OAuth2-Proxy client ID..."
if ! command -v jq >/dev/null 2>&1; then
    echo "Error: jq is required but not installed" >&2
    exit 1
fi

CLIENT_ID=$(/opt/keycloak/bin/kcadm.sh get clients -r "$REALM_NAME" --fields id,clientId | \
    jq -r '.[] | select(.clientId=="portfolio-oauth2-proxy") | .id' 2>/dev/null)

if [ -z "$CLIENT_ID" ] || [ "$CLIENT_ID" = "null" ]; then
    echo "Error: OAuth2-Proxy client not found" >&2
    exit 1
fi

echo "Updating OAuth2-Proxy client secret..."
if ! /opt/keycloak/bin/kcadm.sh update "clients/$CLIENT_ID" \
    -r "$REALM_NAME" \
    -s secret="$OAUTH2_PROXY_CLIENT_SECRET"; then
    echo "Error: Failed to update OAuth2-Proxy client secret" >&2
    exit 1
fi

echo "Keycloak secrets configured successfully!"