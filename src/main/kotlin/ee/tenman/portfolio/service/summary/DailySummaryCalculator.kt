package ee.tenman.portfolio.service.summary

import ee.tenman.portfolio.domain.PortfolioDailySummary
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.model.metrics.PortfolioMetrics
import ee.tenman.portfolio.service.calculation.InvestmentMetricsService
import ee.tenman.portfolio.service.calculation.XirrCalculationService
import ee.tenman.portfolio.service.pricing.PriceLookup
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Component
class DailySummaryCalculator(
  private val investmentMetricsService: InvestmentMetricsService,
  private val xirrCalculationService: XirrCalculationService,
) {
  fun calculateFromTransactions(
    transactions: List<PortfolioTransaction>,
    date: LocalDate,
    priceLookup: PriceLookup? = null,
  ): PortfolioDailySummary {
    if (transactions.isEmpty()) return createEmptySummary(date)
    val baselineProfit = calculateBaselineProfit(transactions, date, priceLookup)
    val metrics = investmentMetricsService.calculatePortfolioMetrics(transactions.groupBy { it.instrument }, date, priceLookup)
    val inception = transactions.minOf { it.transactionDate }
    val earningsPerDay = calculateEarningsPerDay(metrics.totalProfit, baselineProfit, inception, date)
    return buildSummary(date, metrics, earningsPerDay)
  }

  fun shouldReuseYesterday(
    yesterdaySummary: PortfolioDailySummary,
    todaySummary: PortfolioDailySummary,
  ): Boolean = yesterdaySummary.totalValue.compareTo(todaySummary.totalValue) == 0

  fun calculateEarningsPerDay(
    totalProfit: BigDecimal,
    baselineProfit: BigDecimal,
    inception: LocalDate,
    date: LocalDate,
  ): BigDecimal {
    val age = ChronoUnit.DAYS.between(inception, date).coerceAtLeast(1)
    val window = minOf(age, TRAILING_WINDOW_DAYS)
    return totalProfit.subtract(baselineProfit).divide(BigDecimal(window), CALCULATION_SCALE, RoundingMode.HALF_UP)
  }

  fun createEmptySummary(date: LocalDate): PortfolioDailySummary =
    PortfolioDailySummary(
      entryDate = date,
      totalValue = BigDecimal.ZERO,
      xirrAnnualReturn = BigDecimal.ZERO,
      realizedProfit = BigDecimal.ZERO,
      unrealizedProfit = BigDecimal.ZERO,
      totalProfit = BigDecimal.ZERO,
      earningsPerDay = BigDecimal.ZERO,
    )

  private fun calculateBaselineProfit(
    transactions: List<PortfolioTransaction>,
    date: LocalDate,
    priceLookup: PriceLookup?,
  ): BigDecimal {
    val baselineDate = date.minusDays(TRAILING_WINDOW_DAYS)
    val earlier = transactions.filter { !it.transactionDate.isAfter(baselineDate) }
    if (earlier.isEmpty()) return BigDecimal.ZERO
    return investmentMetricsService.calculatePortfolioMetrics(earlier.groupBy { it.instrument }, baselineDate, priceLookup).totalProfit
  }

  private fun buildSummary(
    date: LocalDate,
    metrics: PortfolioMetrics,
    earningsPerDay: BigDecimal,
  ): PortfolioDailySummary {
    val xirr = xirrCalculationService.calculateAdjustedXirr(metrics.xirrCashFlows, date)
    val xirrBigDecimal = xirr?.let { BigDecimal(it) } ?: BigDecimal.ZERO
    return PortfolioDailySummary(
      entryDate = date,
      totalValue = metrics.totalValue.setScale(CALCULATION_SCALE, RoundingMode.HALF_UP),
      xirrAnnualReturn = xirrBigDecimal.setScale(CALCULATION_SCALE, RoundingMode.HALF_UP),
      realizedProfit = metrics.realizedProfit.setScale(CALCULATION_SCALE, RoundingMode.HALF_UP),
      unrealizedProfit = metrics.unrealizedProfit.setScale(CALCULATION_SCALE, RoundingMode.HALF_UP),
      totalProfit = metrics.totalProfit.setScale(CALCULATION_SCALE, RoundingMode.HALF_UP),
      earningsPerDay = earningsPerDay.setScale(CALCULATION_SCALE, RoundingMode.HALF_UP),
    )
  }

  companion object {
    private const val TRAILING_WINDOW_DAYS = 365L
    private const val CALCULATION_SCALE = 10
  }
}
