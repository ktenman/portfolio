package ee.tenman.portfolio.auth.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.io.Serializable

@JsonIgnoreProperties(ignoreUnknown = true)
data class UserInfo(
  @JsonProperty("email")
  val email: String,

  @JsonProperty("name")
  val name: String,

  @JsonProperty("givenName")
  val givenName: String,

  @JsonProperty("familyName")
  val familyName: String,

  @JsonProperty("picture")
  val picture: String
) : Serializable
