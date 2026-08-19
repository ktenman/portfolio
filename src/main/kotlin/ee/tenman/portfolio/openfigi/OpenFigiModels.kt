package ee.tenman.portfolio.openfigi

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

data class OpenFigiQuery(
  val idType: String,
  val idValue: String,
  val exchCode: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class OpenFigiEntry(
  val data: List<OpenFigiMatch>? = null,
  val warning: String? = null,
  val error: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class OpenFigiMatch(
  val figi: String?,
  val name: String?,
  val ticker: String?,
  val exchCode: String?,
  val securityType: String?,
)
