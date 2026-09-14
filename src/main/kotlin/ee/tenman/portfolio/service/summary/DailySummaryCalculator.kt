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
    val baselineDate = date.minusDays(TRAILING_WINDOW_DAYS)
    val baselineProfit = calculateBaselineProfit(transactions, baselineDate, priceLookup)
    val metrics = metricsOn(transactions, date, priceLookup)
    val days = ChronoUnit.DAYS.between(transactions.minOf { it.transactionDate }, date).coerceIn(1, TRAILING_WINDOW_DAYS)
    val earningsPerDay = metrics.totalProfit.subtract(baselineProfit).divide(BigDecimal(days), CALCULATION_SCALE, RoundingMode.HALF_UP)
    return buildSummary(date, metrics, earningsPerDay)
  }

  fun shouldReuseYesterday(
    yesterdaySummary: PortfolioDailySummary,
    todaySummary: PortfolioDailySummary,
  ): Boolean = yesterdaySummary.totalValue.compareTo(todaySummary.totalValue) == 0

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
    baselineDate: LocalDate,
    priceLookup: PriceLookup?,
  ): BigDecimal {
    val earlier = transactions.filter { !it.transactionDate.isAfter(baselineDate) }
    if (earlier.isEmpty()) return BigDecimal.ZERO
    return metricsOn(earlier, baselineDate, priceLookup).totalProfit
  }

  private fun metricsOn(
    transactions: List<PortfolioTransaction>,
    date: LocalDate,
    priceLookup: PriceLookup?,
  ): PortfolioMetrics = investmentMetricsService.calculatePortfolioMetrics(transactions.groupBy { it.instrument }, date, priceLookup)

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
