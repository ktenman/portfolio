package ee.tenman.portfolio.auth.model

import java.io.Serializable

data class UserInfo(
  val email: String,
  val name: String,
  val givenName: String,
  val familyName: String,
  val picture: String
) : Serializable
