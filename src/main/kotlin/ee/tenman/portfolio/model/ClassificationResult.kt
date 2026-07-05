package ee.tenman.portfolio.model

data class ClassificationResult(
  val success: Int,
  val failure: Int,
  val skipped: Int,
) {
  fun requireAnySuccess(domain: String) {
    check(success > 0 || failure == 0) { "$domain classification failed for all $failure holdings" }
  }
}
