package ee.tenman.portfolio.service.prediction

import ch.tutteli.atrium.api.fluent.en_GB.toBeGreaterThan
import ch.tutteli.atrium.api.fluent.en_GB.toBeLessThan
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioDailySummary
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.TransactionType
import ee.tenman.portfolio.testing.fixture.TransactionFixtures
import ee.tenman.portfolio.service.summary.SummaryCacheService
import ee.tenman.portfolio.service.summary.SummaryService
import ee.tenman.portfolio.service.transaction.TransactionCacheService
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class ReturnPredictionServiceTest {
  private val summaryCacheService = mockk<SummaryCacheService>()
  private val summaryService = mockk<SummaryService>()
  private val transactionCacheService = mockk<TransactionCacheService>()
  private val clock = mockk<Clock>()
  private lateinit var service: ReturnPredictionService

  private val today = LocalDate.of(2025, 6, 15)

  @BeforeEach
  fun setup() {
    service = ReturnPredictionService(summaryCacheService, summaryService, transactionCacheService, clock)
    val fixedInstant = ZonedDateTime.of(2025, 6, 15, 12, 0, 0, 0, ZoneId.systemDefault()).toInstant()
    every { clock.instant() } returns fixedInstant
    every { clock.zone } returns ZoneId.systemDefault()
    every { transactionCacheService.getAllTransactions() } returns emptyList()
  }

  @Test
  fun `should return empty predictions when fewer than 30 data points`() {
    val summaries = createSummaries(15, BigDecimal("10000"), BigDecimal("0.10"))
    every { summaryCacheService.getAllDailySummaries() } returns summaries
    every { summaryService.getCurrentDaySummary() } returns summaries.last()
    val result = service.predict()
    expect(result.predictions).toHaveSize(0)
    expect(result.dataPointCount).toEqual(15)
  }

  @Test
  fun `should return four horizon predictions with sufficient data`() {
    val summaries = createGrowingSummaries(60, BigDecimal("10000"), 0.0003)
    every { summaryCacheService.getAllDailySummaries() } returns summaries
    every { summaryService.getCurrentDaySummary() } returns summaries.last()
    val result = service.predict()
    expect(result.predictions).toHaveSize(4)
    expect(result.predictions[0].horizon).toEqual("1M")
    expect(result.predictions[1].horizon).toEqual("3M")
    expect(result.predictions[2].horizon).toEqual("6M")
    expect(result.predictions[3].horizon).toEqual("1Y")
  }

  @Test
  fun `should calculate expected value using xirr compound growth`() {
    val summaries = createGrowingSummaries(60, BigDecimal("10000"), 0.0003)
    val currentSummary =
      PortfolioDailySummary(
      entryDate = summaries.last().entryDate,
      totalValue = summaries.last().totalValue,
      xirrAnnualReturn = BigDecimal("0.10"),
      totalProfit = BigDecimal("500.00"),
      earningsPerDay = BigDecimal("1.37"),
    )
    every { summaryCacheService.getAllDailySummaries() } returns summaries
    every { summaryService.getCurrentDaySummary() } returns currentSummary
    val result = service.predict()
    val oneYearPrediction = result.predictions.first { it.horizon == "1Y" }
    val expectedXirr = currentSummary.totalValue.toDouble() * Math.pow(1.10, 365.0 / 365.25)
    expect(oneYearPrediction.expectedValue.setScale(0, RoundingMode.HALF_UP))
      .toEqualNumerically(BigDecimal(expectedXirr).setScale(0, RoundingMode.HALF_UP))
  }

  @Test
  fun `should produce wider confidence intervals for longer horizons`() {
    val summaries = createGrowingSummaries(60, BigDecimal("10000"), 0.0003)
    every { summaryCacheService.getAllDailySummaries() } returns summaries
    every { summaryService.getCurrentDaySummary() } returns summaries.last()
    val result = service.predict()
    val ranges = result.predictions.map { it.optimisticValue.subtract(it.pessimisticValue) }
    expect(ranges[1]).toBeGreaterThan(ranges[0])
    expect(ranges[2]).toBeGreaterThan(ranges[1])
    expect(ranges[3]).toBeGreaterThan(ranges[2])
  }

  @Test
  fun `should handle zero volatility without error`() {
    val summaries = createSummaries(60, BigDecimal("10000"), BigDecimal("0.10"))
    every { summaryCacheService.getAllDailySummaries() } returns summaries
    every { summaryService.getCurrentDaySummary() } returns summaries.last()
    val result = service.predict()
    expect(result.predictions).toHaveSize(4)
    result.predictions.forEach { prediction ->
      expect(prediction.expectedValue).toEqualNumerically(prediction.optimisticValue)
      expect(prediction.expectedValue).toEqualNumerically(prediction.pessimisticValue)
    }
  }

  @Test
  fun `should handle negative xirr annual return`() {
    val summaries = createGrowingSummaries(60, BigDecimal("10000"), -0.0005)
    val currentSummary =
      PortfolioDailySummary(
      entryDate = summaries.last().entryDate,
      totalValue = summaries.last().totalValue,
      xirrAnnualReturn = BigDecimal("-0.15"),
      totalProfit = BigDecimal("-200.00"),
      earningsPerDay = BigDecimal("-0.50"),
    )
    every { summaryCacheService.getAllDailySummaries() } returns summaries
    every { summaryService.getCurrentDaySummary() } returns currentSummary
    val result = service.predict()
    val oneYearPrediction = result.predictions.first { it.horizon == "1Y" }
    expect(oneYearPrediction.expectedValue).toBeLessThan(currentSummary.totalValue)
  }

  @Test
  fun `should use clock for target date calculation`() {
    val summaries = createGrowingSummaries(60, BigDecimal("10000"), 0.0003)
    every { summaryCacheService.getAllDailySummaries() } returns summaries
    every { summaryService.getCurrentDaySummary() } returns summaries.last()
    val result = service.predict()
    expect(result.predictions[0].targetDate).toEqual(today.plusDays(30))
    expect(result.predictions[1].targetDate).toEqual(today.plusDays(91))
    expect(result.predictions[2].targetDate).toEqual(today.plusDays(183))
    expect(result.predictions[3].targetDate).toEqual(today.plusDays(365))
  }

  @Test
  fun `should filter out zero values from log return calculation`() {
    val summaries = createGrowingSummaries(60, BigDecimal("10000"), 0.0003).toMutableList()
    summaries[10] =
      PortfolioDailySummary(
      entryDate = summaries[10].entryDate,
      totalValue = BigDecimal.ZERO,
      xirrAnnualReturn = BigDecimal.ZERO,
      totalProfit = BigDecimal.ZERO,
      earningsPerDay = BigDecimal.ZERO,
    )
    every { summaryCacheService.getAllDailySummaries() } returns summaries
    every { summaryService.getCurrentDaySummary() } returns summaries.last()
    val result = service.predict()
    expect(result.predictions).toHaveSize(4)
  }

  @Test
  fun `should include monthly contributions in predictions`() {
    val summaries = createGrowingSummaries(60, BigDecimal("10000"), 0.0003)
    val instrument = TransactionFixtures.createInstrument(symbol = "VWCE", name = "Vanguard FTSE All-World")
    val transactions = createMonthlyBuyTransactions(instrument, BigDecimal("500"), 3)
    every { summaryCacheService.getAllDailySummaries() } returns summaries
    every { summaryService.getCurrentDaySummary() } returns summaries.last()
    every { transactionCacheService.getAllTransactions() } returns transactions
    val result = service.predict()
    expect(result.monthlyInvestment.toDouble()).toBeGreaterThan(0.0)
    expect(
      result.predictions
      .first { it.horizon == "1Y" }
      .contributions
      .toDouble(),
        ).toBeGreaterThan(0.0)
  }

  @Test
  fun `should return zero monthly investment when no transactions`() {
    val summaries = createGrowingSummaries(60, BigDecimal("10000"), 0.0003)
    every { summaryCacheService.getAllDailySummaries() } returns summaries
    every { summaryService.getCurrentDaySummary() } returns summaries.last()
    val result = service.predict()
    expect(result.monthlyInvestment).toEqualNumerically(BigDecimal.ZERO)
    result.predictions.forEach { expect(it.contributions).toEqualNumerically(BigDecimal.ZERO) }
  }

  private fun createMonthlyBuyTransactions(
    instrument: Instrument,
    amount: BigDecimal,
    months: Int,
  ): List<PortfolioTransaction> =
    (0 until months).map { i ->
      PortfolioTransaction(
        instrument = instrument,
        transactionType = TransactionType.BUY,
        quantity = BigDecimal.ONE,
        price = amount,
        transactionDate = today.minusMonths((months - i).toLong()),
        platform = Platform.LIGHTYEAR,
      )
    }

  private fun createSummaries(
    count: Int,
    totalValue: BigDecimal,
    xirrAnnualReturn: BigDecimal,
  ): List<PortfolioDailySummary> =
    (0 until count).map { i ->
      PortfolioDailySummary(
        entryDate = today.minusDays((count - 1 - i).toLong()),
        totalValue = totalValue,
        xirrAnnualReturn = xirrAnnualReturn,
        totalProfit = BigDecimal("500.00"),
        earningsPerDay = BigDecimal("1.37"),
      )
    }

  private fun createGrowingSummaries(
    count: Int,
    startValue: BigDecimal,
    dailyReturn: Double,
  ): List<PortfolioDailySummary> =
    (0 until count).map { i ->
      val value = startValue.toDouble() * Math.pow(1 + dailyReturn, i.toDouble())
      PortfolioDailySummary(
        entryDate = today.minusDays((count - 1 - i).toLong()),
        totalValue = BigDecimal(value).setScale(10, RoundingMode.HALF_UP),
        xirrAnnualReturn = BigDecimal(dailyReturn * 365.25).setScale(10, RoundingMode.HALF_UP),
        totalProfit = BigDecimal("500.00"),
        earningsPerDay = BigDecimal("1.37"),
      )
    }
}
