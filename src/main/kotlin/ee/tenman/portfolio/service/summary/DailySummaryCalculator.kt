package ee.tenman.portfolio.service.summary

import ee.tenman.portfolio.domain.PortfolioDailySummary
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.model.metrics.PortfolioMetrics
import ee.tenman.portfolio.model.metrics.PreparedDay
import ee.tenman.portfolio.model.metrics.PreparedHolding
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
  ): PortfolioDailySummary = calculateFromPrepared(prepareDay(transactions, date, priceLookup, ::prepareHoldings), priceLookup)

  fun prepareDay(
    transactions: List<PortfolioTransaction>,
    date: LocalDate,
    priceLookup: PriceLookup?,
    holdings: (List<PortfolioTransaction>) -> List<PreparedHolding>,
  ): PreparedDay =
    PreparedDay(
      date = date,
      baselineProfit = calculateBaselineProfit(transactions, baselineDate(date), priceLookup, holdings),
      trailingDays = trailingDays(transactions, date),
      holdings = holdings(transactions),
    )

  fun holdingsByPrefix(sortedTransactions: List<PortfolioTransaction>): (List<PortfolioTransaction>) -> List<PreparedHolding> {
    val prepared =
      object : LinkedHashMap<Int, List<PreparedHolding>>(RECENT_PREFIXES * 2, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, List<PreparedHolding>>): Boolean = size > RECENT_PREFIXES
      }
    val prefixes =
      sortedTransactions.groupBy { it.instrument }.mapValues { (instrument, history) ->
        investmentMetricsService.prepareHoldingPrefixes(instrument, history)
      }
    return { prefix ->
      prepared.getOrPut(prefix.size) {
        prefix.groupingBy { it.instrument }.eachCount().map { (instrument, count) -> prefixes.getValue(instrument)[count - 1] }
      }
    }
  }

  fun calculateFromPrepared(
    day: PreparedDay,
    priceLookup: PriceLookup?,
  ): PortfolioDailySummary {
    if (day.holdings.isEmpty()) return createEmptySummary(day.date)
    val metrics = investmentMetricsService.valuePortfolio(day.holdings, day.date, priceLookup)
    return buildSummary(day, metrics)
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

  private fun prepareHoldings(transactions: List<PortfolioTransaction>): List<PreparedHolding> =
    investmentMetricsService.prepareHoldings(transactions.groupBy { it.instrument })

  private fun calculateBaselineProfit(
    transactions: List<PortfolioTransaction>,
    baselineDate: LocalDate,
    priceLookup: PriceLookup?,
    holdings: (List<PortfolioTransaction>) -> List<PreparedHolding>,
  ): BigDecimal {
    val earlier = transactions.filter { !it.transactionDate.isAfter(baselineDate) }
    if (earlier.isEmpty()) return BigDecimal.ZERO
    return investmentMetricsService.valuePortfolio(holdings(earlier), baselineDate, priceLookup).totalProfit
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
    day: PreparedDay,
    metrics: PortfolioMetrics,
  ): PortfolioDailySummary {
    val date = day.date
    val earningsPerDay =
      metrics.totalProfit.subtract(day.baselineProfit).divide(BigDecimal(day.trailingDays), CALCULATION_SCALE, RoundingMode.HALF_UP)
    val xirr = xirrCalculationService.calculateAdjustedXirr(metrics.xirrCashFlows, date, day::xirrSchedule)
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
    private const val RECENT_PREFIXES = 2
    private const val LOAD_FACTOR = 0.75f
  }
}
