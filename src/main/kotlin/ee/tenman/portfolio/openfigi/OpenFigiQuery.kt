package ee.tenman.portfolio.openfigi

data class OpenFigiQuery(
  val idType: String,
  val idValue: String,
  val exchCode: String? = null,
)
