FROM node:lts-iron as build

WORKDIR /app
# Copying files separately can help in utilizing Docker's cache mechanism efficiently.
COPY package.json package-lock.json ./
RUN npm install

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

# Update packages including busybox to fix CVE-2024-58251 and CVE-2025-46394
RUN apk update && \
    apk upgrade --no-cache busybox busybox-binsh ssl_client && \
    apk add --no-cache curl && \
    rm -rf /var/cache/apk/*

COPY --from=build /app/dist /usr/share/nginx/html
COPY ui/frontend-nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
