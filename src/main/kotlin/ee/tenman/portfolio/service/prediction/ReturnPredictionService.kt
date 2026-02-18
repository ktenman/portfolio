package ee.tenman.portfolio.service.prediction

import ee.tenman.portfolio.domain.TransactionType
import ee.tenman.portfolio.dto.HorizonPredictionDto
import ee.tenman.portfolio.dto.ReturnPredictionDto
import ee.tenman.portfolio.service.summary.SummaryCacheService
import ee.tenman.portfolio.service.summary.SummaryService
import ee.tenman.portfolio.service.transaction.TransactionCacheService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

@Service
class ReturnPredictionService(
  private val summaryCacheService: SummaryCacheService,
  private val summaryService: SummaryService,
  private val transactionCacheService: TransactionCacheService,
  private val clock: Clock,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  companion object {
    private const val MINIMUM_DATA_POINTS = 30
    private const val CONFIDENCE_Z_SCORE = 1.0
    private const val MAX_ANNUAL_VOLATILITY = 0.30
    private const val DAYS_PER_YEAR = 365.25
    private const val DAYS_PER_MONTH = 30.44
    private const val SCALE = 10
    private val HORIZONS =
      listOf(
      "1M" to 30,
      "3M" to 91,
      "6M" to 183,
      "1Y" to 365,
      "2Y" to 730,
    )
  }

  fun predict(): ReturnPredictionDto {
    val currentSummary = summaryService.getCurrentDaySummary()
    val summaries = summaryCacheService.getAllDailySummaries().sortedBy { it.entryDate }
    val values = summaries.map { it.totalValue }
    val monthlyInvestment = calculateTypicalMonthlyInvestment()
    if (values.size < MINIMUM_DATA_POINTS) {
      log.info("Insufficient data for predictions: ${values.size} data points")
      return emptyPrediction(currentSummary.totalValue, currentSummary.xirrAnnualReturn, monthlyInvestment, values.size)
    }
    val logReturns = calculateLogReturns(values)
    if (logReturns.isEmpty()) {
      return emptyPrediction(currentSummary.totalValue, currentSummary.xirrAnnualReturn, monthlyInvestment, values.size)
    }
    val mu = calculateMean(logReturns)
    val sigma = calculateStdDev(logReturns, mu)
    val stats = VolatilityStats(mu, sigma)
    val today = LocalDate.now(clock)
    val predictions =
      HORIZONS.map { (label, days) ->
      projectHorizon(currentSummary.totalValue, currentSummary.xirrAnnualReturn, stats, days, label, today, monthlyInvestment)
    }
    return ReturnPredictionDto(
      currentValue = currentSummary.totalValue,
      xirrAnnualReturn = currentSummary.xirrAnnualReturn,
      dailyVolatility = BigDecimal(sigma).setScale(SCALE, RoundingMode.HALF_UP),
      dataPointCount = values.size,
      monthlyInvestment = monthlyInvestment,
      predictions = predictions,
    )
  }

  private fun calculateTypicalMonthlyInvestment(): BigDecimal {
    val transactions = transactionCacheService.getAllTransactions()
    val buyTransactions =
      transactions
      .filter { it.transactionType == TransactionType.BUY }
      .filterNot { it.instrument.isCash() }
    if (buyTransactions.isEmpty()) return BigDecimal.ZERO
    val sorted =
      buyTransactions
      .groupBy { YearMonth.from(it.transactionDate) }
      .mapValues { (_, txs) -> txs.sumOf { it.quantity.multiply(it.price).add(it.commission) } }
      .values
      .sortedBy { it }
    if (sorted.size < 3) return BigDecimal.ZERO
    return sorted[sorted.size / 4].setScale(SCALE, RoundingMode.HALF_UP)
  }

  private fun calculateLogReturns(values: List<BigDecimal>): List<Double> =
    values
      .zipWithNext()
      .filter { (prev, curr) -> prev.signum() > 0 && curr.signum() > 0 }
      .map { (prev, curr) -> ln(curr.toDouble() / prev.toDouble()) }

  private fun calculateMean(values: List<Double>): Double = values.sum() / values.size

  private fun calculateStdDev(
    values: List<Double>,
    mean: Double,
  ): Double {
    if (values.size < 2) return 0.0
    val variance = values.sumOf { (it - mean) * (it - mean) } / (values.size - 1)
    return sqrt(variance)
  }

  private fun projectHorizon(
    currentValue: BigDecimal,
    xirrAnnualReturn: BigDecimal,
    stats: VolatilityStats,
    horizonDays: Int,
    label: String,
    today: LocalDate,
    monthlyInvestment: BigDecimal,
  ): HorizonPredictionDto {
    val t = horizonDays.toDouble()
    val xirrRate = xirrAnnualReturn.toDouble()
    val months = t / DAYS_PER_MONTH
    val contributions = monthlyInvestment.toDouble() * months
    val expected = currentValue.toDouble() * (1 + xirrRate).pow(t / DAYS_PER_YEAR) + contributions
    val annualizedSigma = minOf(stats.sigma * sqrt(DAYS_PER_YEAR), MAX_ANNUAL_VOLATILITY)
    val timeInYears = t / DAYS_PER_YEAR
    val diffusion = CONFIDENCE_Z_SCORE * annualizedSigma * sqrt(timeInYears)
    val optimistic = expected * exp(diffusion)
    val pessimistic = expected * exp(-diffusion)
    return HorizonPredictionDto(
      horizon = label,
      horizonDays = horizonDays,
      targetDate = today.plusDays(horizonDays.toLong()),
      expectedValue = BigDecimal(expected).setScale(SCALE, RoundingMode.HALF_UP),
      optimisticValue = BigDecimal(optimistic).setScale(SCALE, RoundingMode.HALF_UP),
      pessimisticValue = BigDecimal(pessimistic).setScale(SCALE, RoundingMode.HALF_UP),
      contributions = BigDecimal(contributions).setScale(SCALE, RoundingMode.HALF_UP),
    )
  }

  private fun emptyPrediction(
    currentValue: BigDecimal,
    xirrAnnualReturn: BigDecimal,
    monthlyInvestment: BigDecimal,
    dataPointCount: Int,
  ) = ReturnPredictionDto(
      currentValue = currentValue,
      xirrAnnualReturn = xirrAnnualReturn,
      dailyVolatility = BigDecimal.ZERO,
      dataPointCount = dataPointCount,
      monthlyInvestment = monthlyInvestment,
      predictions = emptyList(),
    )
}
