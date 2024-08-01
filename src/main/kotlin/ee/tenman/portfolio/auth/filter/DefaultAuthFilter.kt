package ee.tenman.portfolio.auth.filter

import ee.tenman.portfolio.auth.AuthService
import ee.tenman.portfolio.auth.UserContextHolder
import ee.tenman.portfolio.auth.model.AuthStatus
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
@Profile("default")
class DefaultAuthFilter(private val authService: AuthService) : OncePerRequestFilter()  {

  override fun shouldNotFilter(request: HttpServletRequest): Boolean {
    return request.requestURI.startsWith("/actuator/health")
  }

  override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
    val sessionId = request.cookies?.find { it.name == "AUTHSESSION" }?.value

    if (sessionId == null) {
      response.status = HttpStatus.UNAUTHORIZED.value()
      return
    }

    val authResponse = try {
      authService.getAuthResponse(sessionId)
    } catch (e: Exception) {
      response.status = HttpStatus.UNAUTHORIZED.value()
      return
    }

    if (authResponse.status == AuthStatus.UNAUTHORIZED) {
      response.status = HttpStatus.UNAUTHORIZED.value()
      return
    }

    val userInfo = authResponse.user
    UserContextHolder.setUserInfo(userInfo)
    try {
      request.setAttribute("userInfo", userInfo)
      filterChain.doFilter(request, response)
    } finally {
      UserContextHolder.clear()
    }
  }
}
