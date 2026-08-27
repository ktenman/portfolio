package ee.tenman.portfolio.domain

import java.math.BigDecimal
import java.time.LocalDate

data class DailyPricePoint(
  val instrumentId: Long,
  val entryDate: LocalDate,
  val closePrice: BigDecimal,
)
