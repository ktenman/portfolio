package ee.tenman.portfolio.service.summary

import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.PortfolioDailySummary
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.model.metrics.PortfolioMetrics
import ee.tenman.portfolio.service.calculation.InvestmentMetricsService
import ee.tenman.portfolio.service.calculation.XirrCalculationService
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

@Component
class DailySummaryCalculator(
  private val investmentMetricsService: InvestmentMetricsService,
  private val xirrCalculationService: XirrCalculationService,
) {
  fun calculateFromTransactions(
    transactions: List<PortfolioTransaction>,
    date: LocalDate,
  ): PortfolioDailySummary {
    if (transactions.isEmpty()) return createEmptySummary(date)
    val instrumentGroups = transactions.groupBy { it.instrument }
    return calculateFromInstrumentGroups(instrumentGroups, date)
  }

  fun calculateFromInstrumentGroups(
    instrumentGroups: Map<Instrument, List<PortfolioTransaction>>,
    date: LocalDate,
  ): PortfolioDailySummary {
    if (instrumentGroups.isEmpty()) return createEmptySummary(date)
    val metrics = investmentMetricsService.calculatePortfolioMetrics(instrumentGroups, date)
    return buildSummary(date, metrics)
  }

  fun shouldReuseYesterday(
    yesterdaySummary: PortfolioDailySummary,
    todaySummary: PortfolioDailySummary,
  ): Boolean = yesterdaySummary.totalValue.compareTo(todaySummary.totalValue) == 0

  fun calculateEarningsPerDay(
    totalValue: BigDecimal,
    xirrRate: BigDecimal,
  ): BigDecimal =
    totalValue
      .multiply(xirrRate)
      .divide(DAYS_PER_YEAR, CALCULATION_SCALE, RoundingMode.HALF_UP)

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

  private fun buildSummary(
    date: LocalDate,
    metrics: PortfolioMetrics,
  ): PortfolioDailySummary {
    val xirr = xirrCalculationService.calculateAdjustedXirr(metrics.xirrCashFlows, date)
    val xirrBigDecimal = xirr?.let { BigDecimal(it) } ?: BigDecimal.ZERO
    val earningsPerDay = calculateEarningsPerDay(metrics.totalValue, xirrBigDecimal)
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
    private val DAYS_PER_YEAR = BigDecimal("365.25")
    private const val CALCULATION_SCALE = 10
  }
}
