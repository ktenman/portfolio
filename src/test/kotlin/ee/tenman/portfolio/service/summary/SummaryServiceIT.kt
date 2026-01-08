package ee.tenman.portfolio.service.summary

import ch.tutteli.atrium.api.fluent.en_GB.notToEqualNull
import ch.tutteli.atrium.api.fluent.en_GB.toBeGreaterThan
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.IntegrationTest
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.TransactionType
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.repository.PortfolioDailySummaryRepository
import ee.tenman.portfolio.repository.PortfolioTransactionRepository
import jakarta.annotation.Resource
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate

@IntegrationTest
class SummaryServiceIT {
  @Resource
  private lateinit var summaryService: SummaryService

  @Resource
  private lateinit var instrumentRepository: InstrumentRepository

  @Resource
  private lateinit var transactionRepository: PortfolioTransactionRepository

  @Resource
  private lateinit var summaryRepository: PortfolioDailySummaryRepository

  @Resource
  private lateinit var clock: Clock

  private lateinit var instrument: Instrument

  @BeforeEach
  fun setUp() {
    instrument =
      instrumentRepository.save(
      Instrument(
        symbol = "TEST:EUR",
        name = "Test Instrument",
        category = "ETF",
        baseCurrency = "EUR",
        currentPrice = BigDecimal("100.00"),
      ),
    )
  }

  @Test
  fun `should calculateSummaryForDate return summary when no transactions exist`() {
    val date = LocalDate.of(2024, 7, 1)

    val summary = summaryService.calculateSummaryForDate(date)

    expect(summary.entryDate).toEqual(date)
    expect(summary.totalValue).toEqualNumerically(BigDecimal.ZERO)
    expect(summary.totalProfit).toEqualNumerically(BigDecimal.ZERO)
  }

  @Test
  fun `should calculateSummaryForDate return summary with single transaction`() {
    val transactionDate = LocalDate.of(2024, 6, 15)
    val calculationDate = LocalDate.of(2024, 7, 1)
    transactionRepository.save(
      PortfolioTransaction(
        instrument = instrument,
        transactionType = TransactionType.BUY,
        quantity = BigDecimal("10"),
        price = BigDecimal("90.00"),
        transactionDate = transactionDate,
        platform = Platform.TRADING212,
      ),
    )

    val summary = summaryService.calculateSummaryForDate(calculationDate)

    expect(summary.entryDate).toEqual(calculationDate)
  }

  @Test
  fun `should calculateSummaryForDate return consistent results for same date`() {
    val transactionDate = LocalDate.of(2024, 6, 1)
    val calculationDate = LocalDate.of(2024, 7, 1)
    transactionRepository.save(
      PortfolioTransaction(
        instrument = instrument,
        transactionType = TransactionType.BUY,
        quantity = BigDecimal("5"),
        price = BigDecimal("80.00"),
        transactionDate = transactionDate,
        platform = Platform.TRADING212,
      ),
    )

    val summary1 = summaryService.calculateSummaryForDate(calculationDate)
    val summary2 = summaryService.calculateSummaryForDate(calculationDate)

    expect(summary1.totalValue).toEqualNumerically(summary2.totalValue)
    expect(summary1.totalProfit).toEqualNumerically(summary2.totalProfit)
    expect(summary1.xirrAnnualReturn).toEqualNumerically(summary2.xirrAnnualReturn)
  }

  @Test
  fun `should calculateSummaryForDate handle multiple transactions`() {
    val date1 = LocalDate.of(2024, 6, 1)
    val date2 = LocalDate.of(2024, 6, 15)
    val calculationDate = LocalDate.of(2024, 7, 1)
    transactionRepository.save(
      PortfolioTransaction(
        instrument = instrument,
        transactionType = TransactionType.BUY,
        quantity = BigDecimal("10"),
        price = BigDecimal("90.00"),
        transactionDate = date1,
        platform = Platform.TRADING212,
      ),
    )
    transactionRepository.save(
      PortfolioTransaction(
        instrument = instrument,
        transactionType = TransactionType.BUY,
        quantity = BigDecimal("5"),
        price = BigDecimal("95.00"),
        transactionDate = date2,
        platform = Platform.TRADING212,
      ),
    )

    val summary = summaryService.calculateSummaryForDate(calculationDate)

    expect(summary.entryDate).toEqual(calculationDate)
  }

  @Test
  fun `should calculateSummaryForDate handle buy and sell transactions`() {
    val buyDate = LocalDate.of(2024, 6, 1)
    val sellDate = LocalDate.of(2024, 6, 20)
    val calculationDate = LocalDate.of(2024, 7, 1)
    transactionRepository.save(
      PortfolioTransaction(
        instrument = instrument,
        transactionType = TransactionType.BUY,
        quantity = BigDecimal("10"),
        price = BigDecimal("90.00"),
        transactionDate = buyDate,
        platform = Platform.TRADING212,
      ),
    )
    transactionRepository.save(
      PortfolioTransaction(
        instrument = instrument,
        transactionType = TransactionType.SELL,
        quantity = BigDecimal("3"),
        price = BigDecimal("100.00"),
        transactionDate = sellDate,
        platform = Platform.TRADING212,
      ),
    )

    val summary = summaryService.calculateSummaryForDate(calculationDate)

    expect(summary.entryDate).toEqual(calculationDate)
  }

  @Test
  fun `should recalculateAllDailySummaries handle empty transactions`() {
    val count = summaryService.recalculateAllDailySummaries()

    expect(count).toEqual(0)
  }

  @Test
  fun `should recalculateAllDailySummaries calculate summaries for date range`() {
    val transactionDate = LocalDate.now(clock).minusDays(5)
    transactionRepository.save(
      PortfolioTransaction(
        instrument = instrument,
        transactionType = TransactionType.BUY,
        quantity = BigDecimal("10"),
        price = BigDecimal("90.00"),
        transactionDate = transactionDate,
        platform = Platform.TRADING212,
      ),
    )

    val count = summaryService.recalculateAllDailySummaries()

    expect(count).toBeGreaterThan(0)
    val summaries = summaryRepository.findAll()
    expect(summaries.size).toBeGreaterThan(0)
  }

  @Test
  fun `should saveDailySummary create new summary when none exists`() {
    val summary = summaryService.calculateSummaryForDate(LocalDate.of(2024, 7, 1))

    val saved = summaryService.saveDailySummary(summary)

    expect(saved.id).toBeGreaterThan(0L)
    val found = summaryRepository.findByEntryDate(summary.entryDate)
    expect(found).notToEqualNull()
  }

  @Test
  fun `should saveDailySummary update existing summary`() {
    val date = LocalDate.of(2024, 7, 1)
    val summary1 = summaryService.calculateSummaryForDate(date)
    summaryService.saveDailySummary(summary1)
    transactionRepository.save(
      PortfolioTransaction(
        instrument = instrument,
        transactionType = TransactionType.BUY,
        quantity = BigDecimal("10"),
        price = BigDecimal("90.00"),
        transactionDate = date.minusDays(10),
        platform = Platform.TRADING212,
      ),
    )
    val summary2 = summaryService.calculateSummaryForDate(date)

    val saved = summaryService.saveDailySummary(summary2)

    val all = summaryRepository.findAll().filter { it.entryDate == date }
    expect(all.size).toEqual(1)
    expect(saved.totalValue).toEqualNumerically(summary2.totalValue)
  }

  @Test
  fun `should getDailySummariesBetween return summaries in date range`() {
    val start = LocalDate.of(2024, 7, 1)
    val end = LocalDate.of(2024, 7, 5)
    val summary1 = summaryService.calculateSummaryForDate(start)
    val summary2 = summaryService.calculateSummaryForDate(end)
    summaryService.saveDailySummary(summary1)
    summaryService.saveDailySummary(summary2)

    val result = summaryService.getDailySummariesBetween(start, end)

    expect(result.size).toEqual(2)
  }
}
