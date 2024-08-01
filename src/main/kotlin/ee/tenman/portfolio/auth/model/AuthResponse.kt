package ee.tenman.portfolio.auth.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.io.Serializable

@JsonIgnoreProperties(ignoreUnknown = true)
data class AuthResponse(
  @JsonProperty("status")
  val status: AuthStatus,

  @JsonProperty("user")
  val user: UserInfo? = null,

  @JsonProperty("authorities")
  val authorities: List<String> = emptyList(),

  @JsonProperty("provider")
  val provider: String? = null,

  @JsonProperty("message")
  val message: String? = null
) : Serializable
