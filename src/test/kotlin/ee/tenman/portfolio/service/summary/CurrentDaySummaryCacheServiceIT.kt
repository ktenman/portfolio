package ee.tenman.portfolio.service.summary

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
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
import ee.tenman.portfolio.repository.PortfolioTransactionRepository
import io.mockk.every
import jakarta.annotation.Resource
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

@IntegrationTest
class CurrentDaySummaryCacheServiceIT {
  @Resource
  private lateinit var currentDaySummaryCacheService: CurrentDaySummaryCacheService

  @Resource
  private lateinit var instrumentRepository: InstrumentRepository

  @Resource
  private lateinit var portfolioTransactionRepository: PortfolioTransactionRepository

  @Resource
  private lateinit var dailyPriceRepository: DailyPriceRepository

  @MockkBean
  lateinit var clock: Clock

  @Test
  fun `should serve cached current day summary without recomputing when the day advances`() {
    stubClockAt(LocalDate.of(2024, 3, 11))
    seedPortfolio(LocalDate.of(2024, 3, 11))
    currentDaySummaryCacheService.getCurrentDaySummary()
    stubClockAt(LocalDate.of(2024, 3, 12))
    val servedFromCache = currentDaySummaryCacheService.getCurrentDaySummary()
    expect(servedFromCache.entryDate).toEqual(LocalDate.of(2024, 3, 11))
  }

  @Test
  fun `should overwrite cached current day summary with the latest day when refresh is invoked`() {
    stubClockAt(LocalDate.of(2024, 3, 11))
    seedPortfolio(LocalDate.of(2024, 3, 11))
    currentDaySummaryCacheService.getCurrentDaySummary()
    stubClockAt(LocalDate.of(2024, 3, 12))
    currentDaySummaryCacheService.refreshCurrentDaySummary()
    val servedAfterRefresh = currentDaySummaryCacheService.getCurrentDaySummary()
    expect(servedAfterRefresh.entryDate).toEqual(LocalDate.of(2024, 3, 12))
  }

  @Test
  fun `should keep serving the refreshed day from cache after the clock advances again`() {
    stubClockAt(LocalDate.of(2024, 3, 11))
    seedPortfolio(LocalDate.of(2024, 3, 11))
    currentDaySummaryCacheService.getCurrentDaySummary()
    stubClockAt(LocalDate.of(2024, 3, 12))
    currentDaySummaryCacheService.refreshCurrentDaySummary()
    stubClockAt(LocalDate.of(2024, 3, 13))
    val servedFromCache = currentDaySummaryCacheService.getCurrentDaySummary()
    expect(servedFromCache.entryDate).toEqual(LocalDate.of(2024, 3, 12))
  }

  private fun stubClockAt(date: LocalDate) {
    every { clock.instant() } returns Instant.parse("${date}T10:00:00Z")
    every { clock.zone } returns Clock.systemUTC().zone
  }

  private fun seedPortfolio(today: LocalDate) {
    val instrument =
      instrumentRepository.save(
        Instrument(
          symbol = "QDVE",
          name = "iShares S&P 500 Information Technology Sector UCITS ETF — USD",
          category = "ETF",
          baseCurrency = "EUR",
          currentPrice = BigDecimal("28.25"),
        ),
      )
    dailyPriceRepository.save(
      DailyPrice(
        instrument = instrument,
        entryDate = today,
        providerName = ProviderName.FT,
        openPrice = BigDecimal("28.25"),
        highPrice = BigDecimal("28.25"),
        lowPrice = BigDecimal("28.25"),
        closePrice = BigDecimal("28.25"),
        volume = 1000,
      ),
    )
    portfolioTransactionRepository.save(
      PortfolioTransaction(
        instrument = instrument,
        transactionType = TransactionType.BUY,
        quantity = BigDecimal("3.4"),
        price = BigDecimal("27.25"),
        transactionDate = today.minusMonths(2),
        platform = Platform.LIGHTYEAR,
      ),
    )
  }
}
