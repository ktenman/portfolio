package ee.tenman.portfolio.service.pricing

import ch.tutteli.atrium.api.fluent.en_GB.notToEqualNull
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.DailyPricePoint
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class PriceLookupTest {
  private val queryDate = LocalDate.of(2024, 6, 18)

  @Test
  fun `should priceOnOrBefore return the close price on an exact date match`() {
    val lookup = PriceLookup(listOf(createPricePoint(1L, queryDate, BigDecimal("150.25"))))
    expect(lookup.priceOnOrBefore(1L, queryDate)).notToEqualNull().toEqualNumerically(BigDecimal("150.25"))
  }

  @Test
  fun `should priceOnOrBefore return the most recent price before the queried date`() {
    val prices =
      listOf(
        createPricePoint(1L, queryDate.minusDays(10), BigDecimal("100")),
        createPricePoint(1L, queryDate.minusDays(3), BigDecimal("120")),
      )
    val lookup = PriceLookup(prices)
    expect(lookup.priceOnOrBefore(1L, queryDate)).notToEqualNull().toEqualNumerically(BigDecimal("120"))
  }

  @Test
  fun `should priceOnOrBefore return null for an unknown instrument`() {
    val lookup = PriceLookup(listOf(createPricePoint(1L, queryDate, BigDecimal("150"))))
    expect(lookup.priceOnOrBefore(999L, queryDate)).toEqual(null)
  }

  @Test
  fun `should priceOnOrBefore return null when every price is after the queried date`() {
    val lookup = PriceLookup(listOf(createPricePoint(1L, queryDate.plusDays(1), BigDecimal("150"))))
    expect(lookup.priceOnOrBefore(1L, queryDate)).toEqual(null)
  }

  @Test
  fun `should priceOnOrBefore return the price exactly at the ten year lookback boundary`() {
    val lookup = PriceLookup(listOf(createPricePoint(1L, queryDate.minusYears(10), BigDecimal("42.50"))))
    expect(lookup.priceOnOrBefore(1L, queryDate)).notToEqualNull().toEqualNumerically(BigDecimal("42.50"))
  }

  @Test
  fun `should priceOnOrBefore return null when the nearest price is just over ten years old`() {
    val lookup =
      PriceLookup(listOf(createPricePoint(1L, queryDate.minusYears(10).minusDays(1), BigDecimal("42.50"))))
    expect(lookup.priceOnOrBefore(1L, queryDate)).toEqual(null)
  }

  @Test
  fun `should priceOnOrBefore isolate prices between different instruments`() {
    val prices =
      listOf(
        createPricePoint(1L, queryDate, BigDecimal("150")),
        createPricePoint(2L, queryDate, BigDecimal("2800")),
      )
    val lookup = PriceLookup(prices)
    expect(lookup.priceOnOrBefore(2L, queryDate)).notToEqualNull().toEqualNumerically(BigDecimal("2800"))
  }

  private fun createPricePoint(
    instrumentId: Long,
    date: LocalDate,
    closePrice: BigDecimal,
  ): DailyPricePoint = DailyPricePoint(instrumentId = instrumentId, entryDate = date, closePrice = closePrice)
}
