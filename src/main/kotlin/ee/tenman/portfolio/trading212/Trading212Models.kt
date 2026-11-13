package ee.tenman.portfolio.trading212

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.math.BigDecimal

@JsonIgnoreProperties(ignoreUnknown = true)
data class Trading212EtfHolding(
  val ticker: String,
  val percentage: BigDecimal,
  val externalName: String?,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Trading212EtfSummary(
  val description: String?,
  val dividendDistribution: String?,
  val expenseRatio: BigDecimal?,
  val totalNetAssetValue: BigDecimal?,
  val holdingsCount: Int?,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Trading212Instrument(
  val ticker: String,
  val type: String,
  val isin: String?,
  val currencyCode: String?,
  val name: String?,
  val shortName: String?,
)
