package ee.tenman.portfolio.auth.filter

import ee.tenman.portfolio.auth.AuthService
import ee.tenman.portfolio.auth.AuthenticationException
import ee.tenman.portfolio.auth.UserContextHolder
import ee.tenman.portfolio.auth.model.AuthStatus
import ee.tenman.portfolio.domain.UserAccount
import ee.tenman.portfolio.service.UserAccountService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
@Profile("default")
class DefaultAuthFilter(
  private val authService: AuthService,
  private val userAccountService: UserAccountService
) : OncePerRequestFilter() {

  override fun shouldNotFilter(request: HttpServletRequest): Boolean =
    request.requestURI.startsWith("/actuator/health")

  override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
    try {
      val sessionId = request.cookies?.find { it.name == "AUTHSESSION" }?.value
        ?: throw AuthenticationException("No session ID found")

      val userAccount = getUserAccount(sessionId)
      UserContextHolder.setUserAccount(userAccount)
      request.setAttribute("userAccount", userAccount)
      filterChain.doFilter(request, response)
    } catch (e: AuthenticationException) {
      response.sendError(HttpStatus.UNAUTHORIZED.value(), e.message)
    } finally {
      UserContextHolder.clear()
    }
  }

  private fun getUserAccount(sessionId: String): UserAccount =
    userAccountService.findBySessionId(sessionId)
      ?: getAuthenticatedUserAccount(sessionId)

  private fun getAuthenticatedUserAccount(sessionId: String): UserAccount {
    val authResponse = try {
      authService.getAuthResponse(sessionId)
    } catch (e: Exception) {
      throw AuthenticationException("Failed to authenticate session", e)
    }

    if (authResponse.status == AuthStatus.UNAUTHORIZED) {
      throw AuthenticationException("Unauthorized session")
    }

    val userInfo = authResponse.user
      ?: throw AuthenticationException("No user info found for authenticated session")

    return userAccountService.getOrCreateByEmail(userInfo.email).apply {
      if (this.sessionId != sessionId) {
        this.sessionId = sessionId
        userAccountService.save(this)
      }
    }
  }
}
