package ee.tenman.portfolio.dto

import java.math.BigDecimal

data class TransactionSummaryDto(
  val totalRealizedProfit: BigDecimal,
  val totalUnrealizedProfit: BigDecimal,
  val totalProfit: BigDecimal,
)
