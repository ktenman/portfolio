package ee.tenman.portfolio.auth.filter

import ee.tenman.portfolio.auth.UserContextHolder
import ee.tenman.portfolio.auth.model.UserInfo
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
@Profile("local")
class NoAuthFilter : OncePerRequestFilter() {

  override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
    val userInfo = UserInfo(
      email = "user@example.com",
      name = "John Doe",
      givenName = "John",
      familyName = "Doe",
      picture = "https://example.com/profile-picture.jpg"
    )
    UserContextHolder.setUserInfo(userInfo)
    try {
      request.setAttribute("userInfo", userInfo)
      filterChain.doFilter(request, response)
    } finally {
      UserContextHolder.clear()
    }
  }
}
