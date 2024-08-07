package ee.tenman.portfolio.service.xirr

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.apache.commons.math3.analysis.differentiation.DerivativeStructure
import org.apache.commons.math3.analysis.differentiation.UnivariateDifferentiableFunction
import org.apache.commons.math3.analysis.solvers.BisectionSolver
import org.apache.commons.math3.analysis.solvers.NewtonRaphsonSolver
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.pow

class Xirr(private val transactions: List<Transaction>) {
  companion object {
    private const val MAX_ELEVATIONS = 1_000
  }

  private val log = LoggerFactory.getLogger(javaClass)
  private val endDate = transactions.maxOf { it.date }
  private val startDate = transactions.minOf { it.date }
  private val yearsToEnd = { date: LocalDate -> ChronoUnit.DAYS.between(date, endDate).toDouble() / 365.25 }

  fun getTransactions(): List<Transaction> = transactions

  fun calculate(): Double = runBlocking {
    log.info("Starting XIRR calculation with ${transactions.size} transactions")
    log.info("Start date: $startDate, End date: $endDate")

    require(transactions.size >= 2) { "Must have at least two transactions" }
    require(transactions.any { it.amount > 0 } && transactions.any { it.amount < 0 }) {
      "Need both positive and negative transactions"
    }

    if (startDate == endDate) {
      log.info("All transactions are on the same day. Calculating simple return.")
      return@runBlocking calculateSimpleReturn()
    }
    val guess = estimateInitialRate()
    log.info("Initial guess for XIRR: $guess")

    tryConcurrentCalculation(guess)
  }

  private suspend fun tryConcurrentCalculation(guess: Double): Double = coroutineScope {
    val newtonRaphsonDeferred = async { runCatching { calculateXirrWithNewtonRaphson(guess) } }
    val bisectionDeferred = async { runCatching { calculateXirrWithBisection() } }

    val newtonRaphsonResult = newtonRaphsonDeferred.await()
    if (newtonRaphsonResult.isSuccess) {
      log.info("Newton-Raphson method succeeded.")
      return@coroutineScope newtonRaphsonResult.getOrThrow()
    }

    val bisectionResult = bisectionDeferred.await()
    if (bisectionResult.isSuccess) {
      log.info("Bisection method succeeded.")
      return@coroutineScope bisectionResult.getOrThrow()
    }

    log.error("Both Newton-Raphson and Bisection methods failed.")
    throw Exception("XIRR calculation failed using both Newton-Raphson and Bisection methods")
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

      override fun value(t: DerivativeStructure): DerivativeStructure {
        val x = t.value
        val value = netPresentValue(x)
        val derivative = netPresentValueDerivative(x)
        log.debug("NPV for rate $x: $value, Derivative: $derivative")
        return DerivativeStructure(t.freeParameters, t.order, value, derivative)
      }
    }
  }

  private fun netPresentValue(rate: Double): Double =
    transactions.sumOf { it.amount * (1 + rate).pow(yearsToEnd(it.date)) }

  private fun netPresentValueDerivative(rate: Double): Double =
    transactions.sumOf {
      it.amount * yearsToEnd(it.date) * (1 + rate).pow(yearsToEnd(it.date) - 1)
    }

  private fun estimateInitialRate(): Double {
    val totalAmount = transactions.sumOf { it.amount }
    val totalDeposits = transactions.filter { it.amount < 0 }.sumOf { -it.amount }
    val years = ChronoUnit.DAYS.between(startDate, endDate).toDouble() / 365.25
    val estimatedRate = if (years > 0) {
      (totalAmount / totalDeposits).pow(1 / years) - 1
    } else {
      0.0
    }
    return estimatedRate.coerceIn(-0.9, 0.9)
  }
}
