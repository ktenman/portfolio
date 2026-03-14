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

  private fun Double.toScaledBigDecimal(): BigDecimal {
    if (isNaN() || isInfinite()) return BigDecimal.ZERO
    return BigDecimal(this).setScale(SCALE, RoundingMode.HALF_UP)
  }

  companion object {
    private const val MINIMUM_DATA_POINTS = 30
    private const val CONFIDENCE_Z_SCORE = 1.28
    private const val MAX_ANNUAL_VOLATILITY = 0.30
    private const val DAYS_PER_YEAR = 365.25
    private const val MONTHS_PER_YEAR = 12
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
    val summaries = summaryCacheService.getAllDailySummaries()
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
    val sigma = calculateStdDev(logReturns)
    val context =
      PredictionContext(
      currentValue = currentSummary.totalValue,
      xirrAnnualReturn = currentSummary.xirrAnnualReturn,
      monthlyInvestment = monthlyInvestment,
      sigma = sigma,
      today = LocalDate.now(clock),
    )
    val predictions = HORIZONS.map { (label, days) -> projectHorizon(context, days, label) }
    return ReturnPredictionDto(
      currentValue = context.currentValue,
      xirrAnnualReturn = context.xirrAnnualReturn,
      dailyVolatility = sigma.toScaledBigDecimal(),
      dataPointCount = values.size,
      monthlyInvestment = monthlyInvestment,
      predictions = predictions,
    )
  }

  private fun calculateTypicalMonthlyInvestment(): BigDecimal {
    val transactions = transactionCacheService.getAllTransactions()
    val buyTransactions =
      transactions.filter { it.transactionType == TransactionType.BUY && !it.instrument.isCash() }
    if (buyTransactions.isEmpty()) return BigDecimal.ZERO
    val sorted =
      buyTransactions
        .groupBy { YearMonth.from(it.transactionDate) }
        .mapValues { (_, txs) -> txs.sumOf { it.quantity.multiply(it.price).add(it.commission) } }
        .values
        .sorted()
    if (sorted.size < 3) return BigDecimal.ZERO
    return sorted[sorted.size / 2].setScale(SCALE, RoundingMode.HALF_UP)
  }

  private fun calculateLogReturns(values: List<BigDecimal>): List<Double> =
    values
      .zipWithNext()
      .filter { (prev, curr) -> prev.signum() > 0 && curr.signum() > 0 }
      .map { (prev, curr) -> ln(curr.toDouble() / prev.toDouble()) }

  private fun calculateStdDev(values: List<Double>): Double {
    if (values.size < 2) return 0.0
    val mean = values.sum() / values.size
    val variance = values.sumOf { (it - mean) * (it - mean) } / (values.size - 1)
    return sqrt(variance)
  }

  private fun projectHorizon(
    context: PredictionContext,
    horizonDays: Int,
    label: String,
  ): HorizonPredictionDto {
    val timeInYears = horizonDays / DAYS_PER_YEAR
    val xirrRate = maxOf(context.xirrAnnualReturn.toDouble(), -0.99)
    val contributions = context.monthlyInvestment.toDouble() * timeInYears * MONTHS_PER_YEAR
    val expected = context.currentValue.toDouble() * (1 + xirrRate).pow(timeInYears) + contributions
    val annualizedSigma = minOf(context.sigma * sqrt(DAYS_PER_YEAR), MAX_ANNUAL_VOLATILITY)
    val diffusion = CONFIDENCE_Z_SCORE * annualizedSigma * sqrt(timeInYears)
    val optimistic = expected * exp(diffusion)
    val pessimistic = expected * exp(-diffusion)
    return HorizonPredictionDto(
      horizon = label,
      horizonDays = horizonDays,
      targetDate = context.today.plusDays(horizonDays.toLong()),
      expectedValue = expected.toScaledBigDecimal(),
      optimisticValue = optimistic.toScaledBigDecimal(),
      pessimisticValue = pessimistic.toScaledBigDecimal(),
      contributions = contributions.toScaledBigDecimal(),
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
