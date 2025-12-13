package ee.tenman.portfolio.auto24

data class SubmitResponse(
  val status: String,
  val price: String? = null,
  val message: String? = null,
)
