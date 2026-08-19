package ee.tenman.portfolio.domain

import java.math.BigDecimal
import java.time.LocalDate

data class DailyPricePoint(
  val instrumentId: Long,
  val entryDate: LocalDate,
  val closePrice: BigDecimal,
)

object HoldingBlockKey {
  private val NON_ALPHANUMERIC = Regex("[^a-z0-9]+")

  fun of(name: String): String =
    name
      .map { it.lowercaseChar() }
      .joinToString("")
      .split(NON_ALPHANUMERIC)
      .firstOrNull { it.isNotEmpty() }
      ?: ""
}
