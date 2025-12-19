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

WORKDIR /app

COPY package*.json ./
RUN npm ci --omit=dev && \
    rm -rf node_modules/onnxruntime-node/bin/napi-v6/darwin \
           node_modules/onnxruntime-node/bin/napi-v6/win32 \
           ~/.npm

COPY --from=builder /app/dist ./dist
COPY model.onnx ./model.onnx

EXPOSE 3000

HEALTHCHECK --interval=10s --timeout=3s --retries=3 \
  CMD curl_ff117 -sf http://localhost:3000/health || exit 1

CMD ["node", "dist/server.js"]
