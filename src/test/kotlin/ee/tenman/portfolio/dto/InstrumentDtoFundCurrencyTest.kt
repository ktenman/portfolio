package ee.tenman.portfolio.dto

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.Currency
import ee.tenman.portfolio.domain.Instrument
import org.junit.jupiter.api.Test

class InstrumentDtoFundCurrencyTest {
  @Test
  fun `fromEntity carries fundCurrency`() {
    val instrument =
      Instrument(
      symbol = "X:Y:Z",
        name = "N",
        category = "ETF",
        baseCurrency = "EUR",
    ).apply { fundCurrency = Currency.USD }

    val dto = InstrumentDto.fromEntity(instrument)

    expect(dto.fundCurrency).toEqual(Currency.USD)
  }

  @Test
  fun `fromEntity passes null fundCurrency through`() {
    val instrument = Instrument(symbol = "X", name = "N", category = "ETF", baseCurrency = "EUR")

    val dto = InstrumentDto.fromEntity(instrument)

    expect(dto.fundCurrency).toEqual(null)
  }
}
