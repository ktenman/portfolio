package ee.tenman.portfolio.service.summary

import ee.tenman.portfolio.domain.PortfolioDailySummary
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.model.metrics.PortfolioMetrics
import ee.tenman.portfolio.model.metrics.PreparedDay
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
  ): PortfolioDailySummary = calculateFromPrepared(prepareDay(transactions, date, priceLookup), priceLookup)

  fun prepareDay(
    transactions: List<PortfolioTransaction>,
    date: LocalDate,
    priceLookup: PriceLookup?,
  ): PreparedDay =
    PreparedDay(
      date = date,
      baselineProfit = calculateBaselineProfit(transactions, baselineDate(date), priceLookup),
      trailingDays = trailingDays(transactions, date),
      holdings = investmentMetricsService.prepareHoldings(transactions.groupBy { it.instrument }),
    )

  fun calculateFromPrepared(
    day: PreparedDay,
    priceLookup: PriceLookup?,
  ): PortfolioDailySummary {
    if (day.holdings.isEmpty()) return createEmptySummary(day.date)
    val metrics = investmentMetricsService.valuePortfolio(day.holdings, day.date, priceLookup)
    return buildSummary(day.date, metrics, day.baselineProfit, day.trailingDays)
  }

  fun pricedDates(date: LocalDate): List<LocalDate> = listOf(date, baselineDate(date))

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
    return investmentMetricsService.calculatePortfolioMetrics(earlier.groupBy { it.instrument }, baselineDate, priceLookup).totalProfit
  }

  private fun baselineDate(date: LocalDate): LocalDate = date.minusDays(TRAILING_WINDOW_DAYS)

  private fun trailingDays(
    transactions: List<PortfolioTransaction>,
    date: LocalDate,
  ): Long {
    val first = transactions.minOfOrNull { it.transactionDate } ?: date
    return ChronoUnit.DAYS.between(first, date).coerceIn(1, TRAILING_WINDOW_DAYS)
  }

  private fun buildSummary(
    date: LocalDate,
    metrics: PortfolioMetrics,
    baselineProfit: BigDecimal,
    trailingDays: Long,
  ): PortfolioDailySummary {
    val earningsPerDay =
      metrics.totalProfit.subtract(baselineProfit).divide(BigDecimal(trailingDays), CALCULATION_SCALE, RoundingMode.HALF_UP)
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
