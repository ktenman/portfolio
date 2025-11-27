package ee.tenman.portfolio.job

import ch.tutteli.atrium.api.fluent.en_GB.toBeGreaterThan
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.verbs.expect
import com.ninjasquad.springmockk.MockkBean
import ee.tenman.portfolio.configuration.IntegrationTest
import ee.tenman.portfolio.domain.DailyPrice
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.domain.TransactionType
import ee.tenman.portfolio.repository.DailyPriceRepository
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.repository.PortfolioDailySummaryRepository
import ee.tenman.portfolio.service.TransactionService
import io.mockk.every
import jakarta.annotation.Resource
import org.junit.jupiter.api.Test
import org.springframework.test.context.TestPropertySource
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

@IntegrationTest
@TestPropertySource(properties = ["scheduling.enabled=true"])
class DailyPortfolioXirrJobIT {
  @Resource
  private lateinit var transactionService: TransactionService

  @Resource
  private lateinit var dailyPortfolioXirrJob: DailyPortfolioXirrJob

  @Resource
  private lateinit var instrumentRepository: InstrumentRepository

  @Resource
  private lateinit var dailyPriceRepository: DailyPriceRepository

  @Resource
  private lateinit var portfolioDailySummaryRepository: PortfolioDailySummaryRepository

  @MockkBean
  private lateinit var clock: Clock

  @Test
  fun `should not create duplicated rows when triggered multiple times with same data`() {
    every { clock.instant() } returns Instant.parse("2024-07-05T00:00:00Z")
    every { clock.zone } returns Clock.systemUTC().zone
    val instrument =
      Instrument(
        "QDVE.DEX",
        "iShares S&P 500 Information Technology Sector UCITS ETF USD (Acc)",
        "ETF",
        "EUR",
      ).let {
        it.providerName = ProviderName.FT
        instrumentRepository.save(it)
      }
    PortfolioTransaction(
      instrument = instrument,
      transactionType = TransactionType.BUY,
      quantity = BigDecimal("3.37609300"),
      price = BigDecimal("29.62003713"),
      transactionDate = LocalDate.of(2024, 7, 1),
      platform = Platform.SWEDBANK,
    ).let {
      transactionService.saveTransaction(it)
    }
    createDailyPrices(instrument)

    dailyPortfolioXirrJob.execute()
    dailyPortfolioXirrJob.execute()

    val summaries = portfolioDailySummaryRepository.findAll()
    expect(summaries).toHaveSize(4)
    val minSummary = summaries.minByOrNull { it.entryDate }!!
    expect(minSummary.entryDate.toString()).toEqual("2024-07-01")
    expect(minSummary.totalValue.compareTo(BigDecimal("100.4556472150"))).toEqual(0)
    expect(minSummary.xirrAnnualReturn.compareTo(BigDecimal("0E-10"))).toEqual(0)
    expect(minSummary.realizedProfit.compareTo(BigDecimal.ZERO)).toEqual(0)
    expect(minSummary.unrealizedProfit).toBeGreaterThan(BigDecimal.ZERO)
    expect(minSummary.totalProfit.scale()).toBeGreaterThan(0)
    expect(minSummary.earningsPerDay.compareTo(BigDecimal("0E-10"))).toEqual(0)
  }

  private fun createDailyPrices(instrument: Instrument) {
    val prices =
      listOf(
      LocalDate.of(2024, 7, 1) to BigDecimal("29.7550"),
      LocalDate.of(2024, 7, 2) to BigDecimal("29.9600"),
      LocalDate.of(2024, 7, 3) to BigDecimal("30.1650"),
      LocalDate.of(2024, 7, 4) to BigDecimal("30.22"),
    )
    prices.forEach { (date, price) ->
      dailyPriceRepository.save(
        DailyPrice(
          instrument = instrument,
          entryDate = date,
          providerName = ProviderName.FT,
          openPrice = price,
          highPrice = price,
          lowPrice = price,
          closePrice = price,
          volume = 100000,
        ),
      )
    }
  }
}
