package ee.tenman.portfolio.service.integration

data class CompanyClassificationInput(
  val holdingId: Long,
  val name: String,
  val ticker: String?,
  val etfNames: List<String> = emptyList(),
)
