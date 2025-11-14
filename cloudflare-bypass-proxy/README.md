# Cloudflare Bypass Proxy

Generic TypeScript proxy service with adapter pattern for bypassing Cloudflare protection using `curl-impersonate`.

**Modular architecture with pluggable adapters for different services.**

## Quick Start

```bash
docker build --platform linux/amd64 -t cloudflare-bypass-proxy .
docker run -d -p 3000:3000 --platform linux/amd64 cloudflare-bypass-proxy
```

## API Endpoints

### Trading212 Adapter - Fetch Prices

```bash
curl "http://localhost:3000/prices?tickers=WTAIm_EQ,SPYLa_EQ,QDVEd_EQ"
```

**Response:**

```json
{
  "data": {
    "WTAIm_EQ": {
      "b": 74.47,
      "s": 74.47,
      "t": "2025-11-13T11:45:00.878Z"
    },
    "SPYLa_EQ": {
      "b": 14.4774,
      "s": 14.4774,
      "t": "2025-11-13T11:52:29.165Z"
    }
  }
}
```

### WisdomTree Adapter - Fetch ETF Holdings

```bash
curl "http://localhost:3000/wisdomtree/holdings/WTAI"
```

**Response:** HTML content containing ETF holdings data

### Health Check

```bash
curl http://localhost:3000/health
# {"status":"healthy"}
```

### Metrics

```bash
curl http://localhost:3000/metrics
```

**Response:**

```json
{
  "requests_total": 142,
  "errors_total": 3,
  "error_rate": "2.11%"
}
```

## Configuration

| Variable      | Default                     | Description                  |
| ------------- | --------------------------- | ---------------------------- |
| `PORT`        | `3000`                      | Server port                  |
| `CURL_BINARY` | `/usr/local/bin/curl_ff117` | curl-impersonate binary path |

## Local Development

```bash
npm install
CURL_BINARY=/usr/bin/curl node server.js
```

**Note:** Local testing with regular `curl` will fail due to Cloudflare protection. Use Docker for full functionality.

## Architecture

```
Backend (Kotlin/Feign) → HTTP → Cloudflare Bypass Proxy → curl-impersonate-ff117 → Protected Service
```

The proxy service:

1. Uses adapter pattern for pluggable service integrations
2. Each adapter defines routes, handlers, and response types
3. Executes `curl_ff117` (Firefox 117 TLS fingerprint)
4. Bypasses Cloudflare protection
5. Returns JSON or HTML based on adapter configuration

### Available Adapters

- **Trading212**: Fetches instrument prices (JSON response)
- **WisdomTree**: Fetches ETF holdings (HTML response)

## Logs

Simple, clean logs with ISO timestamps:

```
[2025-11-14T15:41:53.594Z] Cloudflare Bypass Proxy listening on port 3000
Registered 2 adapters:
  - GET /prices
  - GET /wisdomtree/holdings/:etfId
[2025-11-14T15:42:13.946Z] [Trading212] Request completed in 221ms
[2025-11-14T15:42:20.123Z] [WisdomTree] Error: Command failed: timeout
```

## Performance

- **Response Time**: ~220ms (warm container)
- **Memory Usage**: ~30-50MB
- **Concurrent Requests**: Supported via async/await
