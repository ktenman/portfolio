package ee.tenman.portfolio.service.calculation.xirr

import ee.tenman.portfolio.exception.XirrCalculationException
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.pow

class Xirr(
  private val cashFlows: List<CashFlow>,
) {
  companion object {
    private const val MAX_ELEVATIONS = 1_000
    private const val SEARCH_LOWER_BOUND = -0.99
    private const val SEARCH_UPPER_BOUND = 0.99
    private const val SOLVER_ACCURACY = 1e-6
  }

  private val log = LoggerFactory.getLogger(javaClass)
  private val endDate = cashFlows.maxOf { it.date }
  private val startDate = cashFlows.minOf { it.date }

  private val yearsToEnd = { date: LocalDate -> ChronoUnit.DAYS.between(date, endDate).toDouble() / 365.25 }

  fun getCashFlows(): List<CashFlow> = cashFlows

  operator fun invoke(): Double = calculate()

  fun calculate(): Double {
    log.debug("Starting XIRR calculation with ${cashFlows.size} cash flows")

    require(cashFlows.size >= 2) { "Must have at least two cash flows" }
    require(cashFlows.any { it.amount > 0 } && cashFlows.any { it.amount < 0 }) {
      "Need both positive and negative cash flows"
    }

    if (startDate == endDate) return calculateSimpleReturn()

    val guess = estimateInitialRate()
    return trySequentialCalculation(guess)
  }

  private fun trySequentialCalculation(guess: Double): Double =
    try {
      calculateXirrWithNewtonRaphson(guess)
    } catch (e: Exception) {
      log.debug("Newton-Raphson method failed: ${e.message}. Falling back to Bisection solver.")
      try {
        calculateXirrWithBisection()
      } catch (bisectionError: Exception) {
        log.error("Both Newton-Raphson and Bisection methods failed")
        throw XirrCalculationException("XIRR calculation failed using both methods", bisectionError)
      }
    }

  private fun calculateXirrWithNewtonRaphson(guess: Double): Double {
    var current = guess
    repeat(MAX_ELEVATIONS) {
      val next = current - netPresentValue(current) / netPresentValueDerivative(current)
      if (abs(next - current) <= SOLVER_ACCURACY) return next
      current = next
    }
    throw ArithmeticException("Newton-Raphson exceeded $MAX_ELEVATIONS evaluations")
  }

  private fun calculateXirrWithBisection(): Double {
    val lowerValue = netPresentValue(SEARCH_LOWER_BOUND)
    val upperValue = netPresentValue(SEARCH_UPPER_BOUND)
    if (lowerValue * upperValue > 0) {
      return if (abs(lowerValue) < abs(upperValue)) SEARCH_LOWER_BOUND else SEARCH_UPPER_BOUND
    }
    var lower = SEARCH_LOWER_BOUND
    var upper = SEARCH_UPPER_BOUND
    repeat(MAX_ELEVATIONS) {
      val middle = (lower + upper) * 0.5
      if (netPresentValue(middle) * lowerValue > 0) lower = middle else upper = middle
      if (abs(upper - lower) <= SOLVER_ACCURACY) return (lower + upper) * 0.5
    }
    throw ArithmeticException("Bisection exceeded $MAX_ELEVATIONS evaluations")
  }

  private fun calculateSimpleReturn(): Double {
    val initialInvestment = -cashFlows.first { it.amount < 0 }.amount
    val finalValue = cashFlows.last { it.amount > 0 }.amount
    return (finalValue - initialInvestment) / initialInvestment
  }

  private fun netPresentValue(rate: Double): Double = cashFlows.sumOf { it.amount * (1 + rate).pow(yearsToEnd(it.date)) }

  private fun netPresentValueDerivative(rate: Double): Double =
    cashFlows.sumOf {
      it.amount * yearsToEnd(it.date) * (1 + rate).pow(yearsToEnd(it.date) - 1)
    }

  private fun estimateInitialRate(): Double {
    val totalAmount = cashFlows.sumOf { it.amount }
    val totalDeposits = cashFlows.filter { it.amount < 0 }.sumOf { -it.amount }
    val years = ChronoUnit.DAYS.between(startDate, endDate).toDouble() / 365.25

    val estimatedRate =
      if (years > 0) {
        (totalAmount / totalDeposits).pow(1 / years) - 1
      } else {
        0.0
      }

    return estimatedRate.coerceIn(-0.9, 0.9)
  }
}
