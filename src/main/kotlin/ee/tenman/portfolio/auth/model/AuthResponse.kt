package ee.tenman.portfolio.auth.model

import java.io.Serializable

data class AuthResponse(
  val status: AuthStatus,
  val user: UserInfo? = null,
  val authorities: List<String> = emptyList(),
  val provider: String? = null,
  val message: String? = null
) : Serializable
