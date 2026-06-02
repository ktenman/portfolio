FROM --platform=$BUILDPLATFORM node:20 as build

WORKDIR /app
# Copying files separately can help in utilizing Docker's cache mechanism efficiently.
COPY package.json package-lock.json ./
RUN npm ci --include=optional

# Copying the rest of the files after npm install ensures that a change in the source
# does not invalidate the Docker cache from the npm install layer.
COPY ui/ ./ui
COPY tsconfig.json ./
COPY tsconfig.node.json ./
COPY vite.config.ts ./

RUN npm run build && find /app -name "index.html"

# Verify that the dist directory exists and contains the expected files.
# This is just for debugging purposes. You can remove this line once everything works fine.
RUN ls -la /app/dist/

FROM nginx:stable-alpine-slim

# Update packages to fix CVE-2024-58251, CVE-2025-46394, and CVE-2025-62408
RUN apk update && \
    apk upgrade --no-cache busybox busybox-binsh ssl_client c-ares && \
    apk add --no-cache curl && \
    rm -rf /var/cache/apk/*

# Run as the unprivileged nginx user (101): grant the binary the bind capability
# so it can still listen on :80, move the pid to a writable path, and own the cache.
RUN apk add --no-cache --virtual .setcap libcap && \
    setcap 'cap_net_bind_service=+ep' /usr/sbin/nginx && \
    apk del .setcap && \
    sed -i 's|^pid .*|pid /tmp/nginx.pid;|' /etc/nginx/nginx.conf && \
    chown -R 101:101 /var/cache/nginx

COPY --from=build /app/dist /usr/share/nginx/html
COPY ui/frontend-nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

USER 101

CMD ["nginx", "-g", "daemon off;"]
