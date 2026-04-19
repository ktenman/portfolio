package ee.tenman.portfolio.service.instrument

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.IntegrationTest
import ee.tenman.portfolio.domain.Currency
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.repository.InstrumentRepository
import jakarta.annotation.Resource
import org.junit.jupiter.api.Test

@IntegrationTest
class InstrumentServiceFundCurrencyIT {
  @Resource
  private lateinit var instrumentRepository: InstrumentRepository

  @Resource
  private lateinit var instrumentService: InstrumentService

  @Test
  fun `updateFundCurrency persists the new value`() {
    val saved =
      instrumentRepository.save(
      Instrument(symbol = "FC_UPD:XETRA:EUR", name = "Fund", category = "ETF", baseCurrency = "EUR"),
    )

    instrumentService.updateFundCurrency(saved.id, Currency.USD)

    val reloaded = instrumentRepository.findById(saved.id).get()
    expect(reloaded.fundCurrency).toEqual(Currency.USD)
  }
}
