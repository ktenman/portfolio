package ee.tenman.portfolio.trading212

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.math.BigDecimal

@JsonIgnoreProperties(ignoreUnknown = true)
data class Trading212EtfSummary(
  val description: String?,
  val dividendDistribution: String?,
  val expenseRatio: BigDecimal?,
  val totalNetAssetValue: BigDecimal?,
  val holdingsCount: Int?,
)
