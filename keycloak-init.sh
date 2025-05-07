#!/bin/bash
set -e

# Wait for Keycloak to be ready
until curl -s --fail http://keycloak:8080/health/ready; do
  echo "Waiting for Keycloak to be ready..."
  sleep 5
done

echo "Logging in to Keycloak admin CLI..."
/opt/keycloak/bin/kcadm.sh config credentials \
  --server http://localhost:8080 \
  --realm master \
  --user ${KEYCLOAK_ADMIN} \
  --password ${KEYCLOAK_ADMIN_PASSWORD}

# Check if realm already exists
REALM_EXISTS=$(/opt/keycloak/bin/kcadm.sh get realms | grep -c '"realm" : "portfolio"' || true)

if [ "$REALM_EXISTS" -eq "0" ]; then
  echo "Creating portfolio realm..."
  /opt/keycloak/bin/kcadm.sh create realms \
    -s realm=portfolio \
    -s enabled=true \
    -s displayName="Portfolio Application" \
    -s registrationAllowed=false \
    -s sslRequired=NONE
else
  echo "Portfolio realm already exists, skipping creation."
fi

# Check if client already exists
CLIENT_EXISTS=$(/opt/keycloak/bin/kcadm.sh get clients -r portfolio | grep -c '"clientId" : "portfolio-app"' || true)

if [ "$CLIENT_EXISTS" -eq "0" ]; then
  echo "Creating portfolio-app client..."
  CLIENT_ID=$(/opt/keycloak/bin/kcadm.sh create clients \
    -r portfolio \
    -s clientId=portfolio-app \
    -s enabled=true \
    -s publicClient=false \
    -s secret=${CLIENT_SECRET} \
    -s redirectUris='["https://fov.ee/*"]' \
    -s webOrigins='["https://fov.ee"]' \
    -s 'attributes={"post.logout.redirect.uris": "https://fov.ee"}' \
    -s directAccessGrantsEnabled=true \
    -i)

  echo "Created client with ID: $CLIENT_ID"
else
  echo "Portfolio-app client already exists, updating its configuration..."
  CLIENT_ID=$(/opt/keycloak/bin/kcadm.sh get clients -r portfolio --fields id,clientId | grep -B2 '"clientId" : "portfolio-app"' | grep '"id"' | cut -d'"' -f4)

  /opt/keycloak/bin/kcadm.sh update clients/$CLIENT_ID -r portfolio \
    -s enabled=true \
    -s publicClient=false \
    -s secret=${CLIENT_SECRET} \
    -s redirectUris='["https://fov.ee/*"]' \
    -s webOrigins='["https://fov.ee"]' \
    -s 'attributes={"post.logout.redirect.uris": "https://fov.ee"}' \
    -s directAccessGrantsEnabled=true

  echo "Updated client with ID: $CLIENT_ID"
fi

# Check if user role exists
ROLE_EXISTS=$(/opt/keycloak/bin/kcadm.sh get roles -r portfolio | grep -c '"name" : "user"' || true)

if [ "$ROLE_EXISTS" -eq "0" ]; then
  echo "Creating user role..."
  /opt/keycloak/bin/kcadm.sh create roles \
    -r portfolio \
    -s name=user \
    -s 'description=Regular user role'
else
  echo "User role already exists, skipping creation."
fi

# Check if Google identity provider exists
IDP_EXISTS=$(/opt/keycloak/bin/kcadm.sh get identity-provider/instances -r portfolio | grep -c '"alias" : "google"' || true)

if [ "$IDP_EXISTS" -eq "0" ]; then
  echo "Creating Google identity provider..."
  /opt/keycloak/bin/kcadm.sh create identity-provider/instances \
    -r portfolio \
    -s alias=google \
    -s providerId=google \
    -s enabled=true \
    -s 'config={"clientId":"'${GOOGLE_CLIENT_ID}'", "clientSecret":"'${GOOGLE_CLIENT_SECRET}'", "useJwksUrl":"true"}'
else
  echo "Google identity provider already exists, updating it..."
  /opt/keycloak/bin/kcadm.sh update identity-provider/instances/google \
    -r portfolio \
    -s enabled=true \
    -s 'config={"clientId":"'${GOOGLE_CLIENT_ID}'", "clientSecret":"'${GOOGLE_CLIENT_SECRET}'", "useJwksUrl":"true"}'
fi

# Add mappers to automatically assign 'user' role to anyone logging in with Google
MAPPER_EXISTS=$(/opt/keycloak/bin/kcadm.sh get identity-provider/instances/google/mappers -r portfolio | grep -c '"name" : "user-role-mapper"' || true)

if [ "$MAPPER_EXISTS" -eq "0" ]; then
  echo "Creating role mapper for Google identity provider..."
  /opt/keycloak/bin/kcadm.sh create identity-provider/instances/google/mappers \
    -r portfolio \
    -s name=user-role-mapper \
    -s identityProviderAlias=google \
    -s identityProviderMapper=hardcoded-attribute-idp-mapper \
    -s config='{"attribute.value": "user", "attribute": "role"}'
else
  echo "Role mapper already exists, skipping creation."
fi

echo "Keycloak initialization completed successfully"
