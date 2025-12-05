package ee.tenman.portfolio.service.xirr

import ee.tenman.portfolio.exception.XirrCalculationException
import org.apache.commons.math3.analysis.differentiation.DerivativeStructure
import org.apache.commons.math3.analysis.differentiation.UnivariateDifferentiableFunction
import org.apache.commons.math3.analysis.solvers.BisectionSolver
import org.apache.commons.math3.analysis.solvers.NewtonRaphsonSolver
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.pow

/**
 * XIRR is the annualized return rate that makes the Net Present Value (NPV) of all cash flows equal to zero.
 * Used for calculating actual investment returns when transactions occur at irregular intervals.
 */
class Xirr(
  private val transactions: List<Transaction>,
) {
  companion object {
    // Maximum iterations for numerical solvers to prevent infinite loops
    private const val MAX_ELEVATIONS = 1_000
  }

  private val log = LoggerFactory.getLogger(javaClass)
  private val endDate = transactions.maxOf { it.date }
  private val startDate = transactions.minOf { it.date }

  private val yearsToEnd = { date: LocalDate -> ChronoUnit.DAYS.between(date, endDate).toDouble() / 365.25 }

  fun getTransactions(): List<Transaction> = transactions

  fun calculate(): Double {
    log.info("Starting XIRR calculation with ${transactions.size} transactions")
    log.info("Start date: $startDate, End date: $endDate")

    require(transactions.size >= 2) { "Must have at least two transactions" }
    require(transactions.any { it.amount > 0 } && transactions.any { it.amount < 0 }) {
      "Need both positive and negative transactions"
    }

    if (startDate == endDate) {
      log.info("All transactions are on the same day. Calculating simple return.")
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

    // Solve for XIRR within reasonable bounds: -99% to +99% annual return
    // These bounds prevent unrealistic returns while covering most real-world investment scenarios
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
    val initialInvestment = -transactions.first { it.amount < 0 }.amount
    val finalValue = transactions.last { it.amount > 0 }.amount
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

      // Provide both NPV and its derivative for Newton-Raphson solver (faster convergence)
      override fun value(t: DerivativeStructure): DerivativeStructure {
        val x = t.value
        val value = netPresentValue(x)
        val derivative = netPresentValueDerivative(x)
        log.debug("NPV for rate $x: $value, Derivative: $derivative")
        return DerivativeStructure(t.freeParameters, t.order, value, derivative)
      }
    }
  }

  // NPV formula: Σ(cashflow × (1 + rate)^years)
  // For XIRR to be valid, we need to find the rate where NPV = 0
  private fun netPresentValue(rate: Double): Double = transactions.sumOf { it.amount * (1 + rate).pow(yearsToEnd(it.date)) }

  // Derivative of NPV with respect to rate: d/dr[Σ(amount × (1+r)^t)] = Σ(amount × t × (1+r)^(t-1))
  // Used by Newton-Raphson method for faster convergence
  private fun netPresentValueDerivative(rate: Double): Double =
    transactions.sumOf {
      it.amount * yearsToEnd(it.date) * (1 + rate).pow(yearsToEnd(it.date) - 1)
    }

  // Estimate initial XIRR rate using simple compound interest formula
  // This provides a better starting point for numerical solvers than arbitrary guesses
  private fun estimateInitialRate(): Double {
    val totalAmount = transactions.sumOf { it.amount }
    val totalDeposits = transactions.filter { it.amount < 0 }.sumOf { -it.amount }
    val years = ChronoUnit.DAYS.between(startDate, endDate).toDouble() / 365.25

    // Simple approximation: (final_value / initial_investment)^(1/years) - 1
    val estimatedRate =
      if (years > 0) {
        (totalAmount / totalDeposits).pow(1 / years) - 1
      } else {
        0.0
      }

    // Constrain to reasonable bounds to ensure solver stability
    return estimatedRate.coerceIn(-0.9, 0.9)
  }
}
