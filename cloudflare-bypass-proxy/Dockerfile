FROM node:24-slim AS curl-source

ADD --checksum=sha256:0d8551cc1620be3c40c6f42d0b46c7077e0fe2a1a28a69d659e49e4f412cd0a0 \
    https://github.com/lexiforest/curl-impersonate/releases/download/v2.3.1/curl-impersonate-v2.3.1.x86_64-linux-gnu.tar.gz \
    /tmp/curl-impersonate.tar.gz
RUN tar -xzf /tmp/curl-impersonate.tar.gz --no-same-owner -C /usr/local/bin curl-impersonate curl_chrome152

FROM node:24-slim AS builder

WORKDIR /app

COPY package*.json ./
RUN ONNXRUNTIME_NODE_INSTALL=skip npm ci

COPY tsconfig.json ./
COPY src ./src

RUN npm run build

FROM node:24-slim

RUN apt-get update && \
    apt-get install -y --no-install-recommends ca-certificates && \
    rm -rf /var/lib/apt/lists/*

COPY --from=curl-source /usr/local/bin/curl-impersonate /usr/local/bin/curl_chrome152 /usr/local/bin/

WORKDIR /app

COPY package*.json ./
RUN ONNXRUNTIME_NODE_INSTALL=skip npm ci --omit=dev && \
    rm -rf node_modules/onnxruntime-node/bin/napi-v6/darwin \
           node_modules/onnxruntime-node/bin/napi-v6/win32 \
           ~/.npm

COPY --from=builder /app/dist ./dist
COPY model.onnx ./model.onnx

EXPOSE 3000

HEALTHCHECK --interval=10s --timeout=3s --retries=3 \
  CMD curl_chrome152 -sf http://localhost:3000/health || exit 1

CMD ["node", "dist/server.js"]
