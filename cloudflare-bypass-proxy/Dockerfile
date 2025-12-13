FROM lwthiker/curl-impersonate:latest AS curl-source

FROM node:20-slim AS builder

WORKDIR /app

COPY package*.json ./
RUN npm ci

COPY tsconfig.json ./
COPY src ./src

RUN npm run build

FROM node:20-slim

COPY --from=curl-source /usr/local/bin/curl_ff117 /usr/local/bin/curl_ff117
COPY --from=curl-source /usr/local/bin/curl-impersonate-ff /usr/local/bin/curl-impersonate-ff
COPY --from=curl-source /usr/lib/x86_64-linux-gnu /usr/lib/x86_64-linux-gnu
RUN chmod +x /usr/local/bin/curl_ff117 /usr/local/bin/curl-impersonate-ff

RUN apt-get update && apt-get install -y --no-install-recommends \
    firefox-esr \
    libgtk-3-0 \
    libdbus-glib-1-2 \
    libxt6 \
    libx11-xcb1 \
    libasound2 \
    libxcomposite1 \
    libxcursor1 \
    libxdamage1 \
    libxfixes3 \
    libxi6 \
    libxrandr2 \
    libxrender1 \
    libxss1 \
    libxtst6 \
    fonts-liberation \
    && rm -rf /var/lib/apt/lists/*

ENV PUPPETEER_SKIP_DOWNLOAD=true \
    FIREFOX_BIN=/usr/bin/firefox-esr \
    MOZ_HEADLESS=1

WORKDIR /app

COPY package*.json ./
RUN npm install --production

COPY --from=builder /app/dist ./dist

EXPOSE 3000

HEALTHCHECK --interval=10s --timeout=3s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://localhost:3000/health || exit 1

CMD ["node", "dist/server.js"]
