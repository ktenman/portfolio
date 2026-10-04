package ee.tenman.portfolio.service.instrument

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.IntegrationTest
import ee.tenman.portfolio.domain.Currency
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.TransactionType
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.repository.PortfolioTransactionRepository
import jakarta.annotation.Resource
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

@IntegrationTest
class InstrumentServiceFundCurrencyIT {
  @Resource
  private lateinit var instrumentRepository: InstrumentRepository

  @Resource
  private lateinit var instrumentService: InstrumentService

  @Resource
  private lateinit var transactions: PortfolioTransactionRepository

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

  @Test
  fun `updateCurrentPrice leaves the stored transactions untouched`() {
    val saved =
      instrumentRepository.save(
      Instrument(symbol = "PX_UPD:XETRA:EUR", name = "Fünd", category = "ETF", baseCurrency = "EUR"),
    )
    val buy =
      transactions.save(
      PortfolioTransaction(saved, TransactionType.BUY, BigDecimal("3"), BigDecimal("10.00"), LocalDate.of(2026, 9, 1), Platform.LIGHTYEAR),
    )

    instrumentService.updateCurrentPrice(saved.id, BigDecimal("12.50"))

    expect(transactions.findById(buy.id).get().version).toEqual(buy.version)
  }
}
