package ee.tenman.portfolio.domain

import java.math.BigDecimal

data class DiversificationAllocationData(
  val instrumentId: Long,
  val value: BigDecimal,
)
