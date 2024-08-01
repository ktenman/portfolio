package ee.tenman.portfolio.auth

import ee.tenman.portfolio.auth.model.AuthResponse
import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.USER_SESSION_CACHE
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.Cacheable
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class AuthService(private val authClient: AuthClient) {
  private val log = LoggerFactory.getLogger(javaClass)

  //  @Cacheable(value = [USER_SESSION_CACHE], key = "#sessionId")
  fun getAuthResponse(sessionId: String): AuthResponse {
    log.info("Checking session: $sessionId")
    val response = authClient.getUser(sessionId)

    log.info("User session $sessionId is valid")
    return response
  }
}
