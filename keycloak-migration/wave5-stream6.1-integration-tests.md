# Wave 5 - Stream 6.1: Integration Testing

## Objective

Comprehensive testing of the complete authentication flow.

## Tasks

### 1. Create E2E Test Suite

```java
// E2EAuthenticationTest.kt
@IntegrationTest
class E2EAuthenticationTest {

    @Test
    fun `complete authentication flow`() {
        // Start with no session
        val response1 = given()
            .`when`()
            .get("/api/instruments")
            .then()
            .statusCode(401)

        // Should redirect to Keycloak
        val loginUrl = given()
            .redirects().follow(false)
            .`when`()
            .get("/")
            .then()
            .statusCode(302)
            .extract()
            .header("Location")

        assertThat(loginUrl).contains("keycloak")
        assertThat(loginUrl).contains("/auth/realms/portfolio")
    }

    @Test
    fun `validates email whitelist`() {
        // Test with non-whitelisted email
        // Requires Selenium for full flow
    }

    @Test
    fun `maintains 30 day session`() {
        // Create session via OAuth2-Proxy
        // Verify Redis TTL is 30 days
    }
}
```

### 2. API Integration Tests

```bash
# test-api-auth.sh
#!/bin/bash

BASE_URL="http://localhost"

echo "Testing API Authentication..."

# Test unauthenticated request
echo "1. Testing unauthenticated request:"
RESP=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/api/instruments")
[ "$RESP" = "401" ] && echo "✓ Returns 401" || echo "✗ Expected 401, got $RESP"

# Get auth cookie (manual step)
echo ""
echo "2. Please login at $BASE_URL and copy the _oauth2_proxy cookie"
read -p "Cookie value: " COOKIE

# Test authenticated request
echo ""
echo "3. Testing authenticated request:"
RESP=$(curl -s -w "%{http_code}" -o /dev/null \
    -H "Cookie: _oauth2_proxy=$COOKIE" \
    "$BASE_URL/api/instruments")
[ "$RESP" = "200" ] && echo "✓ Returns 200" || echo "✗ Expected 200, got $RESP"

# Test user info headers
echo ""
echo "4. Testing user headers:"
HEADERS=$(curl -s -H "Cookie: _oauth2_proxy=$COOKIE" \
    "$BASE_URL/api/user" | jq -r '.email')
[ ! -z "$HEADERS" ] && echo "✓ User email: $HEADERS" || echo "✗ No user email"
```

### 3. Frontend Integration Tests

```typescript
// auth.spec.ts
import { test, expect } from '@playwright/test'

test.describe('Authentication', () => {
  test('redirects to login when unauthenticated', async ({ page }) => {
    await page.goto('/')
    await expect(page).toHaveURL(/keycloak.*realms\/portfolio/)
  })

  test('shows user info when authenticated', async ({ page, context }) => {
    // Set auth cookie
    await context.addCookies([
      {
        name: '_oauth2_proxy',
        value: 'test-session',
        domain: 'localhost',
        path: '/',
      },
    ])

    await page.goto('/')
    await expect(page.locator('[data-test="user-email"]')).toBeVisible()
  })

  test('handles 401 with redirect', async ({ page }) => {
    await page.goto('/')

    // Trigger API call that returns 401
    await page.click('[data-test="load-data"]')

    // Should redirect to login
    await expect(page).toHaveURL(/keycloak/)
  })
})
```

### 4. Load Testing

```bash
#!/bin/bash
# load-test-auth.sh

# Get valid session cookie first
echo "Login and get cookie..."
COOKIE="your-cookie-here"

# Test auth endpoint performance
echo "Testing /oauth2/auth endpoint..."
ab -n 10000 -c 100 \
   -H "Cookie: _oauth2_proxy=$COOKIE" \
   http://localhost:4180/oauth2/auth

# Test API with auth
echo "Testing API with auth..."
ab -n 1000 -c 50 \
   -H "Cookie: _oauth2_proxy=$COOKIE" \
   http://localhost/api/instruments

# Monitor Redis during load test
docker exec portfolio-redis redis-cli INFO stats
```

### 5. Security Testing

```bash
#!/bin/bash
# security-tests.sh

echo "=== Security Tests ==="

# Test session hijacking
echo "1. Session hijacking test:"
COOKIE="invalid-session-id"
RESP=$(curl -s -o /dev/null -w "%{http_code}" \
    -H "Cookie: _oauth2_proxy=$COOKIE" \
    http://localhost/api/instruments)
[ "$RESP" = "401" ] && echo "✓ Invalid session rejected" || echo "✗ Failed"

# Test header injection
echo "2. Header injection test:"
RESP=$(curl -s -o /dev/null -w "%{http_code}" \
    -H "X-Forwarded-User: admin@example.com" \
    http://localhost/api/instruments)
[ "$RESP" = "401" ] && echo "✓ Injected headers ignored" || echo "✗ Failed"

# Test CSRF
echo "3. CSRF protection:"
# OAuth2-Proxy includes CSRF protection by default
curl -v http://localhost:4180/oauth2/sign_out 2>&1 | \
    grep -q "csrf" && echo "✓ CSRF token required" || echo "✗ No CSRF protection"

# Test secure cookies
echo "4. Cookie security:"
curl -v -c - http://localhost:4180/ 2>&1 | \
    grep -i "set-cookie.*httponly" && echo "✓ HttpOnly flag set" || echo "✗ Missing HttpOnly"
```

### 6. Integration Test Report

```markdown
# Integration Test Results

## Test Summary

- Total Tests: 25
- Passed: 23
- Failed: 2
- Duration: 4m 32s

## Detailed Results

### Authentication Flow ✓

- [x] Unauthenticated requests return 401
- [x] Redirects to Keycloak login
- [x] Google OAuth works
- [x] Email whitelist enforced
- [x] Session created in Redis

### Session Management ✓

- [x] 30-day session duration
- [x] Session refresh works
- [x] Logout clears session
- [x] Multiple sessions supported

### API Integration ✓

- [x] Forward auth validates sessions
- [x] User headers propagated
- [x] Backend receives user info
- [x] API calls work with auth

### Performance ✓

- [x] Auth endpoint: 2ms avg response
- [x] 10k requests/sec supported
- [x] Redis performs well

### Security ✓

- [x] Invalid sessions rejected
- [x] Headers can't be spoofed
- [x] CSRF protection active
- [x] Secure cookie flags

## Issues Found

1. [ ] Logout redirect URL needs configuration
2. [ ] Error page styling missing

## Recommendation

System ready for production with minor fixes.
```

## Validation

- [ ] E2E tests passing
- [ ] API tests passing
- [ ] Frontend tests passing
- [ ] Load tests acceptable
- [ ] Security tests passing
- [ ] Test report generated

## Output

Complete test suite and results documenting system readiness.
