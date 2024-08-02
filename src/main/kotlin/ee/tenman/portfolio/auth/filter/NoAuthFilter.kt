package ee.tenman.portfolio.auth.filter

import ee.tenman.portfolio.auth.UserContextHolder
import ee.tenman.portfolio.service.UserAccountService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.*

@Component
@Profile("local", "default")
class NoAuthFilter(private val userAccountService: UserAccountService) : OncePerRequestFilter() {

  override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
    val email = "user@example.com"
    val sessionId = UUID.randomUUID().toString()

    val userAccount = userAccountService.getOrCreateByEmail(email)

    if (userAccount.sessionId != sessionId) {
      userAccount.sessionId = sessionId
      userAccountService.save(userAccount)
    }

    UserContextHolder.setUserAccount(userAccount)
    try {
      request.setAttribute("userAccount", userAccount)
      filterChain.doFilter(request, response)
    } finally {
      UserContextHolder.clear()
    }
  }
}
