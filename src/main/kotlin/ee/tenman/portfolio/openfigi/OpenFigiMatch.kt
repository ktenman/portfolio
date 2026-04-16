package ee.tenman.portfolio.openfigi

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class OpenFigiMatch(
  val figi: String?,
  val name: String?,
  val ticker: String?,
  val exchCode: String?,
  val securityType: String?,
)
