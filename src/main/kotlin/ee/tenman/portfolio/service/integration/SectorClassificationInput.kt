package ee.tenman.portfolio.service.integration

data class SectorClassificationInput(
  val holdingId: Long,
  val name: String,
  val ticker: String? = null,
)
