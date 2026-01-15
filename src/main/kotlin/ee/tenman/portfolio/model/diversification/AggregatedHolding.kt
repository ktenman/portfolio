package ee.tenman.portfolio.model.diversification

import java.math.BigDecimal

data class AggregatedHolding(
  val name: String,
  val ticker: String?,
  val sector: String?,
  val countryCode: String?,
  val countryName: String?,
  val percentage: BigDecimal,
  val etfSymbols: Set<String>,
)
