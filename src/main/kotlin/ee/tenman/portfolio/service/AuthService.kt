package ee.tenman.portfolio.service

import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.stereotype.Component

@Component
class AuthService {
  fun getCurrentUserEmail(): String? {
    return when (val authentication = SecurityContextHolder.getContext().authentication) {
      is OAuth2AuthenticationToken -> authentication.principal.attributes["email"] as String?
      else -> null
    }
  }

  fun isCurrentUserAuthorized(): Boolean {
    return getCurrentUserEmail() != null
  }
}
