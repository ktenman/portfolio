package ee.tenman.portfolio.openfigi

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class OpenFigiEntry(
  val data: List<OpenFigiMatch>? = null,
  val warning: String? = null,
  val error: String? = null,
)
