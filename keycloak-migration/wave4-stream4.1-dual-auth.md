# Wave 4 - Stream 4.1: Implement Dual-Auth Endpoint (Optional)

## Objective

Implement smooth transition by supporting both old and new auth systems temporarily.

## Tasks

### 1. Analyze Current Auth Service

```bash
# Clone auth service
cd /Users/tenman/dev/auth

# Find validate endpoint
grep -r "validate" --include="*.kt" --include="*.java"

# Understand session structure
grep -r "RedisTemplate" --include="*.kt" --include="*.java"
```

### 2. Modify Validate Endpoint

Update the validate endpoint in auth service:

```kotlin
@RestController
class AuthController(
    private val redisTemplate: RedisTemplate<String, Any>,
    private val restTemplate: RestTemplate
) {

    @GetMapping("/validate")
    fun validate(
        request: HttpServletRequest,
        response: HttpServletResponse
    ): ResponseEntity<*> {
        // Check old session first
        val sessionCookie = request.cookies?.find { it.name == "AUTHSESSION" }

        if (sessionCookie != null) {
            val sessionKey = "spring:session:sessions:${sessionCookie.value}"
            val session = redisTemplate.opsForValue().get(sessionKey)

            if (session != null) {
                // Old session still valid
                val userId = extractUserId(session)
                response.setHeader("X-User-Id", userId)
                return ResponseEntity.ok().build()
            }
        }

        // Check new OAuth2-Proxy session
        try {
            val headers = HttpHeaders()
            request.getHeader("Cookie")?.let { headers.add("Cookie", it) }
            request.getHeader("X-Forwarded-Host")?.let { headers.add("X-Forwarded-Host", it) }
            request.getHeader("X-Forwarded-Uri")?.let { headers.add("X-Forwarded-Uri", it) }

            val oauth2Request = HttpEntity<String>(headers)
            val oauth2Response = restTemplate.exchange(
                "http://oauth2-proxy:4180/oauth2/auth",
                HttpMethod.GET,
                oauth2Request,
                String::class.java
            )

            if (oauth2Response.statusCode == HttpStatus.ACCEPTED) {
                // New session valid - copy headers
                oauth2Response.headers.forEach { (name, values) ->
                    if (name.startsWith("X-")) {
                        response.setHeader(name, values.firstOrNull())
                    }
                }
                return ResponseEntity.ok().build()
            }
        } catch (e: Exception) {
            logger.debug("OAuth2-Proxy check failed: ${e.message}")
        }

        // Both auth methods failed
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
    }
}
```

### 3. Add OAuth2-Proxy Client Config

```yaml
# application.yml
oauth2-proxy:
  url: http://oauth2-proxy:4180
  auth-endpoint: /oauth2/auth
  connect-timeout: 2000
  read-timeout: 5000
```

### 4. Build and Deploy Updated Auth Service

```bash
# Build new image
cd /Users/tenman/dev/auth
./gradlew bootBuildImage --imageName=ktenman/auth:dual-auth

# Update docker-compose.local.yml
sed -i '' 's/ktenman\/auth:latest/ktenman\/auth:dual-auth/g' docker-compose.local.yml

# Restart auth service
docker-compose -f docker-compose.local.yml restart auth
```

### 5. Test Dual Auth

```bash
# Test with old session
curl -v http://localhost/api/instruments \
  -H "Cookie: AUTHSESSION=old-session-id"

# Test with new session
curl -v http://localhost/api/instruments \
  -H "Cookie: _oauth2_proxy=new-session-id"

# Test with no session (should fail)
curl -v http://localhost/api/instruments
```

### 6. Add Monitoring

```kotlin
// Add metrics
@Component
class AuthMetrics(private val meterRegistry: MeterRegistry) {
    fun recordAuthAttempt(type: String, success: Boolean) {
        meterRegistry.counter("auth.attempts",
            "type", type,
            "success", success.toString()
        ).increment()
    }
}

// Use in controller
authMetrics.recordAuthAttempt("legacy", true)
authMetrics.recordAuthAttempt("oauth2", false)
```

### 7. Migration Status Endpoint

```kotlin
@GetMapping("/auth/migration-status")
fun migrationStatus(): Map<String, Any> {
    val legacySessions = redisTemplate.keys("spring:session:*").size
    val oauth2Sessions = redisTemplate.keys("*oauth2-proxy*").size

    return mapOf(
        "legacySessions" to legacySessions,
        "oauth2Sessions" to oauth2Sessions,
        "migrationProgress" to (oauth2Sessions.toDouble() / (legacySessions + oauth2Sessions) * 100)
    )
}
```

## Validation

- [ ] Dual auth endpoint implemented
- [ ] Old sessions continue working
- [ ] New sessions work via OAuth2-Proxy
- [ ] Metrics tracking both auth types
- [ ] Migration progress visible
- [ ] No disruption to users

## Output

Updated auth service supporting smooth migration.
