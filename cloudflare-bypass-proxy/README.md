# Cloudflare Bypass Proxy

A lightweight TypeScript proxy service that enables programmatic access to Cloudflare-protected websites by mimicking legitimate browser TLS fingerprints using `curl-impersonate`.

**Built for portfolio management systems that need reliable access to financial data from platforms without public APIs.**

## The Problem

Investment platforms like **Trading212**, **WisdomTree**, and **Lightyear** don't provide public APIs for:

- Real-time stock/ETF prices
- Portfolio holdings data
- ETF composition and holdings breakdowns

Without this data, portfolio management systems cannot:

- Calculate accurate portfolio valuations
- Track performance metrics (XIRR, returns)
- Analyze ETF exposures
- Generate daily portfolio summaries

**Simple web scraping doesn't work** because these platforms use Cloudflare protection that detects and blocks automated requests through sophisticated TLS fingerprinting.

### Why Direct HTTP Requests Fail

When a typical backend (Java, Python, Node.js) tries to scrape these sites:

```bash
curl https://trading212.com/prices/...
# Result: 403 Forbidden or Cloudflare challenge page
```

**Cloudflare detects bots by analyzing:**

1. **TLS Fingerprint** - Unique cipher suites, extensions, and handshake patterns
2. **HTTP/2 Frame Order** - Different from real browsers
3. **Missing Browser Behaviors** - No JavaScript execution, suspicious headers
4. **User Agent Mismatches** - Easy to fake headers, but TLS stack reveals truth

## The Solution

This proxy uses **`curl-impersonate`** to clone a real browser's TLS stack:

```
Portfolio Backend → Cloudflare Bypass Proxy → curl-impersonate (Firefox 117 TLS) → ✅ Cloudflare → Website
```

**How it works:**

1. Backend sends HTTP request to proxy (port 3000)
2. Proxy executes `curl_ff117` with Firefox 117's exact TLS fingerprint
3. Cloudflare sees a "legitimate" Firefox browser and allows the request
4. Proxy returns scraped HTML/JSON data to backend

**Result:** Reliable, automated access to financial data without detection.

📊 **See detailed flow diagram:** [`docs/cloudflare-bypass-flow.puml`](docs/cloudflare-bypass-flow.puml)

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

### Core Components

1. **Express Server** - HTTP API with request routing and middleware
2. **Adapter Pattern** - Pluggable service integrations for different platforms
3. **curl-impersonate** - Binary that clones Firefox 117 browser TLS stack
4. **Request Context** - Tracks requests with unique IDs and service names
5. **Metrics & Logging** - Error rates, response times, request tracking

### How curl-impersonate Works

`curl-impersonate` is not just a modified `curl` with fake headers - it's a complete TLS stack clone:

**Standard curl/HTTP libraries:**

```
TLS Handshake → Generic cipher suites → Cloudflare detects as bot ❌
```

**curl-impersonate-ff117:**

```
TLS Handshake → Exact Firefox 117 cipher suites/extensions → Cloudflare sees Firefox ✅
```

**What gets cloned:**

- TLS cipher suite order (exact match to Firefox 117)
- TLS extensions (SNI, ALPN, Supported Groups, etc.)
- HTTP/2 settings and frame ordering
- Connection preface and window updates

**Why this works:** Cloudflare's bot detection relies heavily on TLS fingerprinting because it's nearly impossible to fake without modifying the underlying TLS library. `curl-impersonate` solves this by using a patched OpenSSL/BoringSSL that replicates browser behavior.

### Available Adapters

Each adapter handles a specific platform with custom parsing logic:

| Adapter        | Endpoint                          | Response Type | Purpose                    |
| -------------- | --------------------------------- | ------------- | -------------------------- |
| **Trading212** | `GET /prices?tickers=...`         | JSON          | Real-time stock/ETF prices |
| **WisdomTree** | `GET /wisdomtree/holdings/:etfId` | HTML          | ETF holdings composition   |
| **Lightyear**  | `GET /lightyear/*`                | JSON/HTML     | Portfolio data and prices  |

**Adding new adapters is simple:**

1. Create adapter file in `src/adapters/`
2. Define route, handler, and response parsing
3. Export from `src/adapters/index.ts`
4. Adapter auto-registers on server startup

## Why This Approach?

### Comparison with Alternatives

| Approach                    | Memory  | Reliability   | Speed  | Complexity |
| --------------------------- | ------- | ------------- | ------ | ---------- |
| **curl-impersonate (this)** | 30-50MB | ✅ High       | ~220ms | Low        |
| **Selenium/Puppeteer**      | 500MB+  | ⚠️ Flaky      | ~3-5s  | High       |
| **Third-party APIs**        | N/A     | ⚠️ Dependency | Varies | Medium     |
| **Direct HTTP requests**    | 10MB    | ❌ Blocked    | N/A    | Low        |

**Why not Selenium/Puppeteer?**

- Requires full browser (Chrome/Firefox) with GUI rendering
- High memory footprint (500MB+ per instance)
- Unreliable in production (requires daily restarts in this project)
- Slow due to browser startup and page rendering overhead

**Why not third-party scraping APIs?**

- Expensive for frequent requests ($0.001-0.01 per request)
- Rate limits and quotas
- External dependency and vendor lock-in
- Privacy concerns with financial data

**Why curl-impersonate wins:**

- Lightweight (comparable to standard HTTP client)
- Reliable (no browser crashes or memory leaks)
- Fast (direct HTTP request with TLS spoofing)
- Self-hosted (no external dependencies)

## Real-World Impact

This proxy enables critical portfolio management features:

**Before (manual process):**

- ❌ Manually check Trading212 for current prices → 15 minutes daily
- ❌ Manually extract ETF holdings from WisdomTree → 30 minutes weekly
- ❌ Calculate portfolio metrics in spreadsheet → error-prone
- ❌ No historical tracking or automated alerts

**After (automated with proxy):**

- ✅ Automated price updates every 1-15 minutes (market-phase adaptive)
- ✅ Automatic ETF holdings refresh and sector classification
- ✅ Real-time XIRR calculations and performance tracking
- ✅ Daily portfolio summaries with 689 automated tests ensuring accuracy
- ✅ Complete audit trail of all price updates and calculations

**Business value:** Transforms hours of manual work into seconds of automated data retrieval, enabling a production-ready portfolio management system that would be impossible without reliable API access.

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
- **Container Startup**: ~2-3 seconds
- **Error Rate**: <3% (typically network/timeout issues)

## Responsible Use

This proxy is designed for **legitimate personal portfolio management**:

✅ **Appropriate use cases:**

- Accessing data you're entitled to see as a customer
- Automating manual tasks you could perform in a browser
- Personal portfolio tracking and performance analysis
- Educational purposes and understanding TLS fingerprinting

❌ **Not intended for:**

- Bypassing paywalls or accessing unauthorized content
- High-frequency scraping that overloads servers
- Commercial data resale or redistribution
- Malicious automation or abuse

**Rate limiting is your responsibility.** Implement appropriate delays and respect the target websites' resources.

**Legal considerations:** Web scraping exists in a legal gray area. This tool mimics legitimate browser behavior for personal data access, similar to manually checking prices. Consult local laws and terms of service before deploying.
