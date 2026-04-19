package ee.tenman.portfolio.repository

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.IntegrationTest
import ee.tenman.portfolio.domain.Currency
import ee.tenman.portfolio.domain.Instrument
import jakarta.annotation.Resource
import org.junit.jupiter.api.Test

@IntegrationTest
class InstrumentFundCurrencyIT {
  @Resource
  private lateinit var instrumentRepository: InstrumentRepository

  @Test
  fun `persists and reads fund currency on instrument`() {
    val saved =
      instrumentRepository.save(
      Instrument(
        symbol = "TEST_FC:XETRA:EUR",
        name = "Test ETF",
        category = "ETF",
        baseCurrency = "EUR",
        fundCurrency = Currency.USD,
      ),
    )
    val reloaded = instrumentRepository.findById(saved.id).get()
    expect(reloaded.fundCurrency).toEqual(Currency.USD)
  }
}
