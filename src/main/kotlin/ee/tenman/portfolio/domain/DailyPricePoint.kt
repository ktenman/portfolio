package ee.tenman.portfolio.domain

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

data class DailyPricePoint(
  val instrumentId: Long,
  val entryDate: LocalDate,
  val closePrice: BigDecimal,
)

data class MinutePricePoint(
  val instrumentId: Long,
  val capturedAt: Instant,
  val price: BigDecimal,
)
