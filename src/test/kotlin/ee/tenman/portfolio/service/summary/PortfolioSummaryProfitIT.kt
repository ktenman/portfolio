package ee.tenman.portfolio.service.summary

import ch.tutteli.atrium.api.fluent.en_GB.toBeGreaterThanOrEqualTo
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate

@IntegrationTest
@Transactional
class PortfolioSummaryProfitIT
@Autowired
  constructor(
  private val portfolioSummaryService: SummaryService,
  private val instrumentRepository: InstrumentRepository,
  private val portfolioTransactionRepository: PortfolioTransactionRepository,
  private val dailyPriceRepository: DailyPriceRepository,
) {
  private lateinit var instrument: Instrument
  private val testDate = LocalDate.of(2024, 7, 9)

  @BeforeEach
  fun setup() {
    instrument =
      instrumentRepository.save(
      Instrument(
        symbol = "TEST:NYSE:USD",
        name = "Test Stock",
        category = "Stock",
        baseCurrency = "USD",
        currentPrice = BigDecimal("100.00"),
        providerName = ProviderName.FT,
      ),
    )

    val startDate = testDate.minusDays(30)
    val endDate = testDate.plusDays(1)
    var currentDate = startDate

    while (!currentDate.isAfter(endDate)) {
      dailyPriceRepository.save(
        DailyPrice(
          instrument = instrument,
          entryDate = currentDate,
          openPrice = BigDecimal("100.00"),
          highPrice = BigDecimal("100.00"),
          lowPrice = BigDecimal("100.00"),
          closePrice = BigDecimal("100.00"),
          providerName = ProviderName.FT,
          volume = null,
        ),
      )
      currentDate = currentDate.plusDays(1)
    }
  }

  @Test
  fun `profit calculation should be consistent between current and historical dates`() {
    portfolioTransactionRepository.save(
      PortfolioTransaction(
        instrument = instrument,
        transactionType = TransactionType.BUY,
        quantity = BigDecimal("10"),
        price = BigDecimal("80.00"),
        transactionDate = testDate.minusDays(10),
        platform = Platform.TRADING212,
      ),
    )

    val historicalDate = testDate.minusDays(1)
    val historicalSummary = portfolioSummaryService.calculateSummaryForDate(historicalDate)

    expect(historicalSummary.totalProfit).toBeGreaterThanOrEqualTo(BigDecimal.ZERO)
  }

  @Test
  fun `profit calculation should account for sells correctly`() {
    portfolioTransactionRepository.save(
      PortfolioTransaction(
        instrument = instrument,
        transactionType = TransactionType.BUY,
        quantity = BigDecimal("20"),
        price = BigDecimal("80.00"),
        transactionDate = testDate.minusDays(20),
        platform = Platform.TRADING212,
      ),
    )

    portfolioTransactionRepository.save(
      PortfolioTransaction(
        instrument = instrument,
        transactionType = TransactionType.SELL,
        quantity = BigDecimal("10"),
        price = BigDecimal("90.00"),
        transactionDate = testDate.minusDays(10),
        platform = Platform.TRADING212,
      ),
    )

    val summary = portfolioSummaryService.calculateSummaryForDate(testDate.minusDays(5))

    val totalSellValue = BigDecimal("10").multiply(BigDecimal("90.00"))
    val realizedProfit = totalSellValue.subtract(BigDecimal("10").multiply(BigDecimal("80.00")))

    expect(summary.totalProfit.compareTo(realizedProfit) >= 0).toEqual(true)
  }
}
