package ee.tenman.portfolio.service.summary

import java.math.BigDecimal
import java.time.LocalDate

data class XirrWindowOpeningPoint(
  val entryDate: LocalDate,
  val totalValue: BigDecimal,
)
