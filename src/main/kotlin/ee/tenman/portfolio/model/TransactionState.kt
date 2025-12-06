package ee.tenman.portfolio.model

import java.math.BigDecimal

data class TransactionState(
  val totalCost: BigDecimal,
  val currentQuantity: BigDecimal,
)
