package ee.tenman.portfolio.service.pricing

import ch.tutteli.atrium.api.fluent.en_GB.notToEqualNull
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.DailyPrice
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.ProviderName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class PriceLookupTest {
  private val queryDate = LocalDate.of(2024, 6, 18)

  @Test
  fun `should priceOnOrBefore return the close price on an exact date match`() {
    val instrument = createInstrument(1L, "AAPL")
    val lookup = PriceLookup(listOf(createDailyPrice(instrument, queryDate, BigDecimal("150.25"))))
    expect(lookup.priceOnOrBefore(1L, queryDate)).notToEqualNull().toEqualNumerically(BigDecimal("150.25"))
  }

  @Test
  fun `should priceOnOrBefore return the most recent price before the queried date`() {
    val instrument = createInstrument(1L, "AAPL")
    val prices =
      listOf(
        createDailyPrice(instrument, queryDate.minusDays(10), BigDecimal("100")),
        createDailyPrice(instrument, queryDate.minusDays(3), BigDecimal("120")),
      )
    val lookup = PriceLookup(prices)
    expect(lookup.priceOnOrBefore(1L, queryDate)).notToEqualNull().toEqualNumerically(BigDecimal("120"))
  }

  @Test
  fun `should priceOnOrBefore return null for an unknown instrument`() {
    val instrument = createInstrument(1L, "AAPL")
    val lookup = PriceLookup(listOf(createDailyPrice(instrument, queryDate, BigDecimal("150"))))
    expect(lookup.priceOnOrBefore(999L, queryDate)).toEqual(null)
  }

  @Test
  fun `should priceOnOrBefore return null when every price is after the queried date`() {
    val instrument = createInstrument(1L, "AAPL")
    val lookup = PriceLookup(listOf(createDailyPrice(instrument, queryDate.plusDays(1), BigDecimal("150"))))
    expect(lookup.priceOnOrBefore(1L, queryDate)).toEqual(null)
  }

  @Test
  fun `should priceOnOrBefore return the price exactly at the ten year lookback boundary`() {
    val instrument = createInstrument(1L, "AAPL")
    val lookup = PriceLookup(listOf(createDailyPrice(instrument, queryDate.minusYears(10), BigDecimal("42.50"))))
    expect(lookup.priceOnOrBefore(1L, queryDate)).notToEqualNull().toEqualNumerically(BigDecimal("42.50"))
  }

  @Test
  fun `should priceOnOrBefore return null when the nearest price is just over ten years old`() {
    val instrument = createInstrument(1L, "AAPL")
    val lookup =
      PriceLookup(listOf(createDailyPrice(instrument, queryDate.minusYears(10).minusDays(1), BigDecimal("42.50"))))
    expect(lookup.priceOnOrBefore(1L, queryDate)).toEqual(null)
  }

  @Test
  fun `should priceOnOrBefore isolate prices between different instruments`() {
    val first = createInstrument(1L, "AAPL")
    val second = createInstrument(2L, "GOOGL")
    val prices =
      listOf(
        createDailyPrice(first, queryDate, BigDecimal("150")),
        createDailyPrice(second, queryDate, BigDecimal("2800")),
      )
    val lookup = PriceLookup(prices)
    expect(lookup.priceOnOrBefore(2L, queryDate)).notToEqualNull().toEqualNumerically(BigDecimal("2800"))
  }

  private fun createInstrument(
    id: Long,
    symbol: String,
  ): Instrument =
    Instrument(
      symbol = symbol,
      name = "$symbol Test",
      category = "Stock",
      baseCurrency = "USD",
    ).apply { this.id = id }

  private fun createDailyPrice(
    instrument: Instrument,
    date: LocalDate,
    closePrice: BigDecimal,
  ): DailyPrice =
    DailyPrice(
      instrument = instrument,
      entryDate = date,
      providerName = ProviderName.FT,
      openPrice = null,
      highPrice = null,
      lowPrice = null,
      closePrice = closePrice,
      volume = null,
    )
}
