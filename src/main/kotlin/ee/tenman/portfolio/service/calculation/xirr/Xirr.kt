package ee.tenman.portfolio.service.calculation.xirr

import ee.tenman.portfolio.exception.XirrCalculationException
import org.apache.commons.math3.analysis.differentiation.DerivativeStructure
import org.apache.commons.math3.analysis.differentiation.UnivariateDifferentiableFunction
import org.apache.commons.math3.analysis.solvers.BisectionSolver
import org.apache.commons.math3.analysis.solvers.NewtonRaphsonSolver
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.pow

class Xirr(
  private val cashFlows: List<CashFlow>,
) {
  companion object {
    private const val MAX_ELEVATIONS = 1_000
  }

  private val log = LoggerFactory.getLogger(javaClass)
  private val endDate = cashFlows.maxOf { it.date }
  private val startDate = cashFlows.minOf { it.date }

  private val yearsToEnd = { date: LocalDate -> ChronoUnit.DAYS.between(date, endDate).toDouble() / 365.25 }

  fun getCashFlows(): List<CashFlow> = cashFlows

  operator fun invoke(): Double = calculate()

  fun calculate(): Double {
    log.info("Starting XIRR calculation with ${cashFlows.size} cash flows")
    log.info("Start date: $startDate, End date: $endDate")

    require(cashFlows.size >= 2) { "Must have at least two cash flows" }
    require(cashFlows.any { it.amount > 0 } && cashFlows.any { it.amount < 0 }) {
      "Need both positive and negative cash flows"
    }

    if (startDate == endDate) {
      log.info("All cash flows are on the same day. Calculating simple return.")
      return calculateSimpleReturn()
    }
    val guess = estimateInitialRate()
    log.info("Initial guess for XIRR: $guess")

    return trySequentialCalculation(guess)
  }

  private fun trySequentialCalculation(guess: Double): Double =
    try {
      log.info("Attempting XIRR calculation with Newton-Raphson solver.")
      calculateXirrWithNewtonRaphson(guess)
    } catch (e: Exception) {
      log.warn("Newton-Raphson method failed: ${e.message}. Falling back to Bisection solver.")
      try {
        calculateXirrWithBisection()
      } catch (bisectionError: Exception) {
        log.error("Both Newton-Raphson and Bisection methods failed.")
        throw XirrCalculationException("XIRR calculation failed using both methods", bisectionError)
      }
    }

  private fun calculateXirrWithNewtonRaphson(guess: Double): Double {
    val solver = NewtonRaphsonSolver()
    log.info("Newton-Raphson solver configuration: maxEvaluations=$MAX_ELEVATIONS")

    val result = solver.solve(MAX_ELEVATIONS, createXirrFunction(), -0.99, 0.99, guess)
    log.info("XIRR calculation result (Newton-Raphson): $result")
    return result
  }

  private fun calculateXirrWithBisection(): Double {
    val solver = BisectionSolver()
    log.info("Bisection solver configuration: maxEvaluations=$MAX_ELEVATIONS")

    val result = solver.solve(MAX_ELEVATIONS, createXirrFunction(), -0.99, 0.99)
    log.info("XIRR calculation result (Bisection): $result")
    return result
  }

  private fun calculateSimpleReturn(): Double {
    val initialInvestment = -cashFlows.first { it.amount < 0 }.amount
    val finalValue = cashFlows.last { it.amount > 0 }.amount
    val simpleReturn = (finalValue - initialInvestment) / initialInvestment
    log.info("Simple return: $simpleReturn")
    return simpleReturn
  }

  private fun createXirrFunction(): UnivariateDifferentiableFunction {
    return object : UnivariateDifferentiableFunction {
      override fun value(x: Double): Double {
        val npv = netPresentValue(x)
        log.debug("NPV for rate $x: $npv")
        return npv
      }

      override fun value(t: DerivativeStructure): DerivativeStructure {
        val x = t.value
        val value = netPresentValue(x)
        val derivative = netPresentValueDerivative(x)
        log.debug("NPV for rate $x: $value, Derivative: $derivative")
        return DerivativeStructure(t.freeParameters, t.order, value, derivative)
      }
    }
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
