# Cloudflare Bypass Proxy

Node.js/TypeScript proxy service using curl-impersonate for TLS fingerprint spoofing to bypass Cloudflare protection. Used by Trading212 and Lightyear integrations.

## Tech Stack

Express 5, TypeScript 5.9, Vitest 5, ONNX Runtime (captcha model), Cheerio (HTML parsing)

## Commands

```bash
npm test                    # Run tests (Vitest)
npm run test:coverage       # Tests with coverage
npm run build               # Compile TypeScript
npm run dev                 # Run with ts-node
```

## Structure

- `src/adapters/` - Platform-specific adapters (Trading212, Lightyear)
- `src/middleware/` - Express middleware
- `src/utils/` - Shared utilities
- `src/types/` - TypeScript type definitions
- `src/__tests__/` - Vitest test files
- `captcha-model/` / `model.onnx` - ONNX captcha solving model

## Testing

- Use Vitest with supertest for HTTP endpoint testing
- Pass supertest a running server, `request(await listen(app))`, never the app: its own `listen(0)` binds `[::]` and connects to `127.0.0.1`, so on macOS the request can reach another process holding that port on IPv4
- Follow the same test naming conventions as the main project (backtick naming)
- Run from project root: `npm run test:proxy`
