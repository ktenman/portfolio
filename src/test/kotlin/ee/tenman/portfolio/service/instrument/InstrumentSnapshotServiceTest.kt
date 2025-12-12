package ee.tenman.portfolio.service.instrument

import ch.tutteli.atrium.api.fluent.en_GB.notToEqualNull
import ch.tutteli.atrium.api.fluent.en_GB.toBeEmpty
import ch.tutteli.atrium.api.fluent.en_GB.toContainExactly
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.domain.TransactionType
import ee.tenman.portfolio.model.PriceChange
import ee.tenman.portfolio.model.metrics.InstrumentMetrics
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.repository.PortfolioTransactionRepository
import ee.tenman.portfolio.service.calculation.HoldingsCalculationService
import ee.tenman.portfolio.service.calculation.InvestmentMetricsService
import ee.tenman.portfolio.service.calculation.XirrCalculationService
import ee.tenman.portfolio.service.calculation.xirr.CashFlow
import ee.tenman.portfolio.service.pricing.DailyPriceService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class InstrumentSnapshotServiceTest {
  private val instrumentRepository = mockk<InstrumentRepository>()
  private val portfolioTransactionRepository = mockk<PortfolioTransactionRepository>()
  private val investmentMetricsService = mockk<InvestmentMetricsService>()
  private val dailyPriceService = mockk<DailyPriceService>()
  private val xirrCalculationService = mockk<XirrCalculationService>()
  private val holdingsCalculationService = mockk<HoldingsCalculationService>()
  private val clock = mockk<Clock>()

  private lateinit var instrumentSnapshotService: InstrumentSnapshotService
  private lateinit var testInstrument: Instrument
  private val testDate = LocalDate.of(2024, 1, 15)
  private val fixedInstant = Instant.parse("2024-01-15T10:00:00Z")

  @BeforeEach
  fun setUp() {
    testInstrument =
      Instrument(
        symbol = "AAPL",
        name = "Apple Inc.",
        category = "Stock",
        baseCurrency = "USD",
        currentPrice = BigDecimal("150.00"),
        providerName = ProviderName.FT,
      ).apply { id = 1L }

    every { clock.instant() } returns fixedInstant
    every { clock.zone } returns ZoneId.of("UTC")

    every { xirrCalculationService.convertToCashFlow(any()) } returns CashFlow(-1000.0, testDate)
    every { xirrCalculationService.calculateAdjustedXirr(any(), any()) } returns 0.15
    instrumentSnapshotService =
      InstrumentSnapshotService(
        instrumentRepository,
        portfolioTransactionRepository,
        investmentMetricsService,
        dailyPriceService,
        xirrCalculationService,
        holdingsCalculationService,
        clock,
      )
  }

  @Test
  fun `should return snapshots with metrics when transactions exist`() {
    val transaction = createBuyCashFlow(BigDecimal("10"), BigDecimal("100"), testDate.minusDays(2))
    val metrics = createMetrics()

    every { instrumentRepository.findAll() } returns listOf(testInstrument)
    every { portfolioTransactionRepository.findAllWithInstruments() } returns listOf(transaction)
    every { investmentMetricsService.calculateInstrumentMetricsWithProfits(testInstrument, any(), any()) } returns metrics
    every { dailyPriceService.getPriceChange(testInstrument, any()) } returns PriceChange(BigDecimal("5.00"), 3.5)

    val result = instrumentSnapshotService.getAllSnapshots()

    expect(result).toHaveSize(1)
    expect(result[0].totalInvestment).toEqualNumerically(BigDecimal("1000"))
    expect(result[0].currentValue).toEqualNumerically(BigDecimal("1500"))
    expect(result[0].profit).toEqualNumerically(BigDecimal("500"))
  }

  @Test
  fun `should filter by platform when platform filter specified`() {
    val lhvTransaction = createBuyCashFlow(BigDecimal("10"), BigDecimal("100"), testDate, Platform.LHV)
    val lightyearTransaction = createBuyCashFlow(BigDecimal("5"), BigDecimal("100"), testDate, Platform.LIGHTYEAR)
    val metrics = createMetrics()

    every { instrumentRepository.findAll() } returns listOf(testInstrument)
    every { portfolioTransactionRepository.findAllWithInstruments() } returns listOf(lhvTransaction, lightyearTransaction)
    every { investmentMetricsService.calculateInstrumentMetricsWithProfits(testInstrument, listOf(lhvTransaction), any()) } returns metrics
    every { dailyPriceService.getPriceChange(testInstrument, any()) } returns null

    val result = instrumentSnapshotService.getAllSnapshots(listOf("lhv"))

    expect(result).toHaveSize(1)
    expect(result[0].platforms).toContainExactly(Platform.LHV)
  }

  @Test
  fun `should return empty when no matching platform transactions`() {
    val lhvTransaction = createBuyCashFlow(BigDecimal("10"), BigDecimal("100"), testDate, Platform.LHV)

    every { instrumentRepository.findAll() } returns listOf(testInstrument)
    every { portfolioTransactionRepository.findAllWithInstruments() } returns listOf(lhvTransaction)

    val result = instrumentSnapshotService.getAllSnapshots(listOf("LIGHTYEAR"))

    expect(result).toBeEmpty()
  }

  @Test
  fun `should return instrument snapshot when no transactions and no platform filter`() {
    every { instrumentRepository.findAll() } returns listOf(testInstrument)
    every { portfolioTransactionRepository.findAllWithInstruments() } returns emptyList()

    val result = instrumentSnapshotService.getAllSnapshots()

    expect(result).toHaveSize(1)
    expect(result[0].instrument).toEqual(testInstrument)
  }

  @Test
  fun `should exclude instruments with zero quantity and zero investment`() {
    val transaction = createBuyCashFlow(BigDecimal("10"), BigDecimal("100"), testDate)
    val zeroMetrics =
      InstrumentMetrics(
      totalInvestment = BigDecimal.ZERO,
      currentValue = BigDecimal.ZERO,
      profit = BigDecimal.ZERO,
      realizedProfit = BigDecimal.ZERO,
      unrealizedProfit = BigDecimal.ZERO,
      xirr = 0.0,
      quantity = BigDecimal.ZERO,
    )

    every { instrumentRepository.findAll() } returns listOf(testInstrument)
    every { portfolioTransactionRepository.findAllWithInstruments() } returns listOf(transaction)
    every { investmentMetricsService.calculateInstrumentMetricsWithProfits(testInstrument, any(), any()) } returns zeroMetrics
    every { dailyPriceService.getPriceChange(testInstrument, any()) } returns null

    val result = instrumentSnapshotService.getAllSnapshots(listOf("lhv"))

    expect(result).toBeEmpty()
  }

  @Test
  fun `should calculate price change from daily price service when holding period sufficient`() {
    val transaction = createBuyCashFlow(BigDecimal("10"), BigDecimal("100"), testDate.minusDays(30))
    val metrics = createMetrics()
    val priceChange = PriceChange(BigDecimal("5.00"), 3.5)

    every { instrumentRepository.findAll() } returns listOf(testInstrument)
    every { portfolioTransactionRepository.findAllWithInstruments() } returns listOf(transaction)
    every { investmentMetricsService.calculateInstrumentMetricsWithProfits(testInstrument, any(), any()) } returns metrics
    every { dailyPriceService.getPriceChange(testInstrument, any()) } returns priceChange

    val result = instrumentSnapshotService.getAllSnapshots()

    expect(result[0].priceChangeAmount).notToEqualNull().toEqualNumerically(BigDecimal("50.00"))
    expect(result[0].priceChangePercent).toEqual(3.5)
  }

  @Test
  fun `should calculate price change since purchase when holding period insufficient`() {
    val transaction = createBuyCashFlow(BigDecimal("10"), BigDecimal("100"), testDate, commission = BigDecimal.ZERO)
    val metrics = createMetrics()

    every { instrumentRepository.findAll() } returns listOf(testInstrument)
    every { portfolioTransactionRepository.findAllWithInstruments() } returns listOf(transaction)
    every { investmentMetricsService.calculateInstrumentMetricsWithProfits(testInstrument, any(), any()) } returns metrics

    val result = instrumentSnapshotService.getAllSnapshots()

    expect(result[0].priceChangeAmount).notToEqualNull().toEqualNumerically(BigDecimal("500.00"))
    expect(result[0].priceChangePercent).toEqual(50.0)
  }

  @Test
  fun `should handle mixed case platform names`() {
    val transaction = createBuyCashFlow(BigDecimal("10"), BigDecimal("100"), testDate, Platform.LHV)
    val metrics = createMetrics()

    every { instrumentRepository.findAll() } returns listOf(testInstrument)
    every { portfolioTransactionRepository.findAllWithInstruments() } returns listOf(transaction)
    every { investmentMetricsService.calculateInstrumentMetricsWithProfits(testInstrument, listOf(transaction), any()) } returns metrics
    every { dailyPriceService.getPriceChange(testInstrument, any()) } returns null

    val result = instrumentSnapshotService.getAllSnapshots(listOf("Lhv"))

    expect(result).toHaveSize(1)
  }

  @Test
  fun `should use current date from clock`() {
    val transaction = createBuyCashFlow(BigDecimal("10"), BigDecimal("100"), testDate)
    val metrics = createMetrics()

    every { instrumentRepository.findAll() } returns listOf(testInstrument)
    every { portfolioTransactionRepository.findAllWithInstruments() } returns listOf(transaction)
    every { investmentMetricsService.calculateInstrumentMetricsWithProfits(testInstrument, any(), testDate) } returns metrics
    every { dailyPriceService.getPriceChange(testInstrument, any()) } returns null

    instrumentSnapshotService.getAllSnapshots()

    verify { investmentMetricsService.calculateInstrumentMetricsWithProfits(testInstrument, any(), testDate) }
  }

  private fun createBuyCashFlow(
    quantity: BigDecimal,
    price: BigDecimal,
    date: LocalDate,
    platform: Platform = Platform.LHV,
    commission: BigDecimal = BigDecimal("5"),
  ): PortfolioTransaction =
    PortfolioTransaction(
      instrument = testInstrument,
      transactionType = TransactionType.BUY,
      quantity = quantity,
      price = price,
      transactionDate = date,
      platform = platform,
      commission = commission,
    )

  private fun createMetrics(): InstrumentMetrics =
    InstrumentMetrics(
      totalInvestment = BigDecimal("1000"),
      currentValue = BigDecimal("1500"),
      profit = BigDecimal("500"),
      realizedProfit = BigDecimal.ZERO,
      unrealizedProfit = BigDecimal("500"),
      xirr = 25.0,
      quantity = BigDecimal("10"),
    )
}
