package ee.tenman.portfolio.model.holding

import ee.tenman.portfolio.domain.Platform
import java.math.BigDecimal

data class InternalHoldingData(
  val holdingId: Long,
  val ticker: String?,
  val name: String,
  val sector: String?,
  val countryCode: String?,
  val countryName: String?,
  val value: BigDecimal,
  val etfSymbol: String,
  val platforms: Set<Platform>,
  val isSynthetic: Boolean = false,
)
