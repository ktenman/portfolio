package ee.tenman.portfolio.model.holding

import java.util.UUID

data class HoldingKey(
  val holdingUuid: UUID?,
  val ticker: String?,
  val name: String,
  val sector: String?,
  val countryCode: String?,
  val countryName: String?,
)
