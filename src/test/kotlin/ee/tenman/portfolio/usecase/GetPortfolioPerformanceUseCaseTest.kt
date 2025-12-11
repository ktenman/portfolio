package ee.tenman.portfolio.usecase

import ch.tutteli.atrium.api.fluent.en_GB.toBeGreaterThan
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.model.metrics.InstrumentMetrics
import ee.tenman.portfolio.repository.PortfolioTransactionRepository
import ee.tenman.portfolio.service.calculation.HoldingsCalculationService
import ee.tenman.portfolio.service.calculation.InvestmentMetricsService
import ee.tenman.portfolio.service.calculation.XirrCalculationService
import ee.tenman.portfolio.service.pricing.DailyPriceService
import ee.tenman.portfolio.service.transaction.TransactionService
import ee.tenman.portfolio.testing.fixture.TransactionFixtures
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.ZoneId

class GetPortfolioPerformanceUseCaseTest {
  private val transactionRepository = mockk<PortfolioTransactionRepository>()
  private val transactionService = mockk<TransactionService>()
  private val dailyPriceService = mockk<DailyPriceService>()
  private val xirrCalculationService = XirrCalculationService()
  private val holdingsCalculationService = HoldingsCalculationService()
  private lateinit var investmentMetricsService: InvestmentMetricsService
  private lateinit var useCase: GetPortfolioPerformanceUseCase

  private val testDate = TransactionFixtures.DEFAULT_DATE
  private val fixedClock = Clock.fixed(testDate.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault())
  private val testInstrument = TransactionFixtures.createInstrument()

  @BeforeEach
  fun setUp() {
    investmentMetricsService =
      InvestmentMetricsService(
        dailyPriceService,
        transactionService,
        xirrCalculationService,
        holdingsCalculationService,
        fixedClock,
      )
    useCase =
      GetPortfolioPerformanceUseCase(
        transactionRepository,
        transactionService,
        investmentMetricsService,
        fixedClock,
      )
    every { transactionService.calculateTransactionProfits(any()) } answers {
      val transactions = firstArg<List<PortfolioTransaction>>()
      transactions.forEach {
        it.unrealizedProfit = BigDecimal.ZERO
        it.realizedProfit = BigDecimal.ZERO
      }
    }
  }

  @Test
  fun `should return empty metrics when no transactions exist`() {
    every { transactionRepository.findAllByInstrumentId(1L) } returns emptyList()
    val result = useCase(1L)
    expect(result).toEqual(InstrumentMetrics.EMPTY)
  }

  @Test
  fun `should calculate metrics for single buy transaction`() {
    val transactions =
      listOf(
      TransactionFixtures.createBuyTransaction(testInstrument, BigDecimal("10"), BigDecimal("100"), testDate.minusDays(30)),
    )
    every { transactionRepository.findAllByInstrumentId(1L) } returns transactions
    val result = useCase(1L)
    expect(result.quantity).toEqualNumerically(BigDecimal("10"))
    expect(result.totalInvestment).toBeGreaterThan(BigDecimal.ZERO)
    expect(result.currentValue).toBeGreaterThan(BigDecimal.ZERO)
  }

  @Test
  fun `should calculate metrics for multiple transactions`() {
    val transactions =
      listOf(
        TransactionFixtures.createBuyTransaction(testInstrument, BigDecimal("10"), BigDecimal("100"), testDate.minusDays(30)),
        TransactionFixtures.createBuyTransaction(testInstrument, BigDecimal("5"), BigDecimal("120"), testDate.minusDays(20)),
        TransactionFixtures.createSellTransaction(testInstrument, BigDecimal("3"), BigDecimal("150"), testDate.minusDays(10)),
      )
    every { transactionRepository.findAllByInstrumentId(1L) } returns transactions
    val result = useCase(1L)
    expect(result.quantity).toEqualNumerically(BigDecimal("12"))
    expect(result.totalInvestment).toBeGreaterThan(BigDecimal.ZERO)
  }

  @Test
  fun `should call transactionService to calculate profits`() {
    val transactions =
      listOf(
      TransactionFixtures.createBuyTransaction(testInstrument, BigDecimal("10"), BigDecimal("100"), testDate.minusDays(30)),
    )
    every { transactionRepository.findAllByInstrumentId(1L) } returns transactions
    useCase(1L)
    verify { transactionService.calculateTransactionProfits(transactions) }
  }

  @Test
  fun `should use invoke operator for cleaner syntax`() {
    val transactions =
      listOf(
      TransactionFixtures.createBuyTransaction(testInstrument, BigDecimal("10"), BigDecimal("100"), testDate.minusDays(30)),
    )
    every { transactionRepository.findAllByInstrumentId(1L) } returns transactions
    val result = useCase(1L)
    expect(result.quantity).toEqualNumerically(BigDecimal("10"))
  }

  @Test
  fun `should calculate metrics using clock for current date`() {
    val transactions =
      listOf(
      TransactionFixtures.createBuyTransaction(testInstrument, BigDecimal("10"), BigDecimal("100"), testDate.minusDays(30)),
    )
    every { transactionRepository.findAllByInstrumentId(1L) } returns transactions
    val result = useCase(1L)
    expect(result.quantity).toEqualNumerically(BigDecimal("10"))
    expect(result.totalInvestment).toBeGreaterThan(BigDecimal.ZERO)
  }

  @Test
  fun `should handle all sold positions returning zero quantity`() {
    val transactions =
      listOf(
        TransactionFixtures.createBuyTransaction(testInstrument, BigDecimal("10"), BigDecimal("100"), testDate.minusDays(30)),
        TransactionFixtures.createSellTransaction(testInstrument, BigDecimal("10"), BigDecimal("150"), testDate.minusDays(10)),
      )
    every { transactionRepository.findAllByInstrumentId(1L) } returns transactions
    val result = useCase(1L)
    expect(result.quantity).toEqualNumerically(BigDecimal.ZERO)
  }
}
