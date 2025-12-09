package ee.tenman.portfolio.model

data class ClassificationResult(
  val success: Int,
  val failure: Int,
  val skipped: Int,
)
