package ee.tenman.portfolio.service.pricing

import ee.tenman.portfolio.domain.DailyPricePoint
import java.math.BigDecimal
import java.time.LocalDate
import java.util.TreeMap

class PriceLookup private constructor(
  private val pricesByInstrument: Map<Long, TreeMap<LocalDate, BigDecimal>>,
  private val pinnedDate: LocalDate? = null,
  private val pinnedPrices: Map<Long, BigDecimal> = emptyMap(),
) {
  constructor(prices: List<DailyPricePoint>) : this(
    prices
      .groupBy { it.instrumentId }
      .mapValues { (_, rows) -> rows.associateTo(TreeMap()) { it.entryDate to it.closePrice } },
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
  ): BigDecimal? {
    pinnedPrice(instrumentId, date)?.let { return it }
    val entry = pricesByInstrument[instrumentId]?.floorEntry(date) ?: return null
    return entry.value.takeUnless { entry.key.isBefore(date.minusYears(LOOKBACK_YEARS)) }
  }

  companion object {
    private const val LOOKBACK_YEARS = 10L
  }
}
