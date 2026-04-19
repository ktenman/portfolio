package ee.tenman.portfolio.job

import ch.tutteli.atrium.api.fluent.en_GB.notToEqualNull
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.IntegrationTest
import ee.tenman.portfolio.configuration.Trading212ScrapingProperties
import ee.tenman.portfolio.domain.Currency
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.lightyear.LightyearFundInfoData
import ee.tenman.portfolio.lightyear.LightyearPriceService
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.service.infrastructure.JobTransactionService
import ee.tenman.portfolio.service.instrument.FundCurrencyResolverService
import ee.tenman.portfolio.service.instrument.InstrumentService
import ee.tenman.portfolio.trading212.Trading212HoldingsService
import io.mockk.every
import io.mockk.mockk
import jakarta.annotation.Resource
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Clock

@IntegrationTest
class TerUpdateJobFundCurrencyIT {
  @Resource
  private lateinit var jobTransactionService: JobTransactionService

  @Resource
  private lateinit var instrumentRepository: InstrumentRepository

  @Resource
  private lateinit var instrumentService: InstrumentService

  @Resource
  private lateinit var trading212HoldingsService: Trading212HoldingsService

  @Resource
  private lateinit var scrapingProperties: Trading212ScrapingProperties

  @Resource
  private lateinit var fundCurrencyResolver: FundCurrencyResolverService

  @Resource
  private lateinit var clock: Clock

  @Test
  fun `persists fund currency from lightyear during ter update`() {
    val instrument =
      instrumentRepository.save(
        Instrument(
          symbol = "JOBFC:GER:EUR",
          name = "Job Fund Currency Test",
          category = "ETF",
          baseCurrency = "EUR",
          providerName = ProviderName.LIGHTYEAR,
        ),
      )
    val lightyearPriceService = mockk<LightyearPriceService>()
    every { lightyearPriceService.fetchFundInfo("JOBFC:GER:EUR") } returns
      LightyearFundInfoData(ter = BigDecimal("0.12"), fundCurrency = Currency.USD)
    val job =
      TerUpdateJob(
        jobTransactionService = jobTransactionService,
        instrumentRepository = instrumentRepository,
        lightyearPriceService = lightyearPriceService,
        instrumentService = instrumentService,
        trading212HoldingsService = trading212HoldingsService,
        scrapingProperties = scrapingProperties,
        fundCurrencyResolver = fundCurrencyResolver,
        clock = clock,
      )

    job.execute()

    val reloaded = instrumentRepository.findById(instrument.id).get()
    expect(reloaded.ter).notToEqualNull().toEqualNumerically(BigDecimal("0.12"))
    expect(reloaded.fundCurrency).toEqual(Currency.USD)
  }
}
