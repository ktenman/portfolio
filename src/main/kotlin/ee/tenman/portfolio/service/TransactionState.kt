package ee.tenman.portfolio.service

import java.math.BigDecimal

data class TransactionState(
  val totalCost: BigDecimal,
  val currentQuantity: BigDecimal,
)
