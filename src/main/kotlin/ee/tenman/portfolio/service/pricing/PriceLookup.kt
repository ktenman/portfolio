package ee.tenman.portfolio.service.pricing

import ee.tenman.portfolio.domain.DailyPricePoint
import java.math.BigDecimal
import java.time.LocalDate

class PriceLookup private constructor(
  private val pricesByInstrument: Map<Long, PriceHistory>,
  private val pinnedDate: LocalDate? = null,
  private val pinnedPrices: Map<Long, BigDecimal> = emptyMap(),
) {
  constructor(prices: List<DailyPricePoint>) : this(
    prices
      .groupBy { it.instrumentId }
      .mapValues { (_, rows) -> PriceHistory.of(rows) },
  )

  fun pinnedAt(
    date: LocalDate,
    prices: Map<Long, BigDecimal>,
  ): PriceLookup = PriceLookup(pricesByInstrument, date, prices.toMap())

  fun pinnedPrice(
    instrumentId: Long,
    date: LocalDate,
  ): BigDecimal? = pinnedPrices[instrumentId].takeIf { date == pinnedDate }

  fun priceOnOrBefore(
    instrumentId: Long,
    date: LocalDate,
  ): BigDecimal? = pinnedPrice(instrumentId, date) ?: pricesByInstrument[instrumentId]?.priceOn(date)
}

private class PriceHistory(
  val days: LongArray,
  val prices: Array<BigDecimal>,
) {
  fun priceOn(date: LocalDate): BigDecimal? {
    val index = floor(date.toEpochDay())
    if (index < 0) return null
    return prices[index].takeUnless { days[index] < date.minusYears(LOOKBACK_YEARS).toEpochDay() }
  }

  private fun floor(day: Long): Int {
    val index = days.binarySearch(day)
    return if (index >= 0) index else -index - 2
  }

  companion object {
    private const val LOOKBACK_YEARS = 10L

    fun of(rows: List<DailyPricePoint>): PriceHistory {
      val sorted = rows.sortedBy { it.entryDate }
      val latest = sorted.filterIndexed { index, row -> index == sorted.lastIndex || sorted[index + 1].entryDate != row.entryDate }
      return PriceHistory(LongArray(latest.size) { latest[it].entryDate.toEpochDay() }, Array(latest.size) { latest[it].closePrice })
    }
  }
}
