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

  @Cacheable(value = [USER_SESSION_CACHE], key = "#sessionId")
  fun getAuthResponse(sessionId: String): AuthResponse {
    val response = authClient.getUser(sessionId)

    if (response.statusCode != HttpStatus.OK) {
      log.error("Unauthorized access attempt by session: $sessionId. Status code: ${response.statusCode}")
      throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized: Invalid session")
    }

    val body = response.body
    if (body == null) {
      log.error("Null response body for session: $sessionId")
      throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Invalid response from auth service")
    }

    log.info("User session $sessionId is valid")
    return body
  }
}
