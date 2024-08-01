package ee.tenman.portfolio.auth.model

import com.fasterxml.jackson.annotation.JsonProperty

enum class AuthStatus {
  @JsonProperty("AUTHORIZED")
  AUTHORIZED,

  @JsonProperty("UNAUTHORIZED")
  UNAUTHORIZED
}
