package ee.tenman.portfolio.service.pricing

import ee.tenman.portfolio.domain.DailyPrice
import java.math.BigDecimal
import java.time.LocalDate
import java.util.TreeMap

class PriceLookup(
  prices: List<DailyPrice>,
) {
  private val pricesByInstrument: Map<Long, TreeMap<LocalDate, BigDecimal>> =
    prices
      .groupBy { it.instrument.id }
      .mapValues { (_, rows) -> rows.associateTo(TreeMap()) { it.entryDate to it.closePrice } }

  fun priceOnOrBefore(
    instrumentId: Long,
    date: LocalDate,
  ): BigDecimal? {
    val prices = pricesByInstrument[instrumentId] ?: return null
    val entry = prices.floorEntry(date) ?: return null
    if (entry.key.isBefore(date.minusYears(LOOKBACK_YEARS))) return null
    return entry.value
  }

  companion object {
    private const val LOOKBACK_YEARS = 10L
  }
}
