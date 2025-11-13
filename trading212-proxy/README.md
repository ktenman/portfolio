# Trading212 Proxy Service

Lightweight REST API wrapper around `curl-impersonate` to fetch Trading212 instrument prices while bypassing Cloudflare protection.

**45 lines of code. One file. Zero dependencies beyond Express.**

## Quick Start

```bash
docker build --platform linux/amd64 -t trading212-proxy .
docker run -d -p 3000:3000 --platform linux/amd64 trading212-proxy
```

## API Endpoints

### Fetch Prices
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

| Variable | Default | Description |
|----------|---------|-------------|
| `PORT` | `3000` | Server port |
| `CURL_BINARY` | `/usr/local/bin/curl_ff117` | curl-impersonate binary path |

## Local Development

```bash
npm install
CURL_BINARY=/usr/bin/curl node server.js
```

**Note:** Local testing with regular `curl` will fail due to Cloudflare protection. Use Docker for full functionality.

## Architecture

```
Backend (Kotlin/Feign) → HTTP → Trading212 Proxy → curl-impersonate-ff117 → Trading212 API
```

The proxy service:
1. Receives HTTP request with ticker symbols
2. Executes `curl_ff117` (Firefox 117 TLS fingerprint)
3. Bypasses Cloudflare protection
4. Returns JSON response

## Logs

Simple, clean logs with ISO timestamps:

```
[2025-11-13T12:06:04.019Z] Trading212 proxy listening on port 3000
[2025-11-13T12:06:13.946Z] Fetched 3 tickers in 221ms
[2025-11-13T12:06:20.123Z] Error: Command failed: timeout
```

## Performance

- **Response Time**: ~220ms (warm container)
- **Memory Usage**: ~30-50MB
- **Concurrent Requests**: Supported via async/await
