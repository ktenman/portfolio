package ee.tenman.portfolio.service.xirr

import org.apache.commons.math3.analysis.differentiation.DerivativeStructure
import org.apache.commons.math3.analysis.differentiation.UnivariateDifferentiableFunction
import org.apache.commons.math3.analysis.solvers.NewtonRaphsonSolver
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.pow

class Xirr(private val transactions: List<Transaction>) {
  private val log = LoggerFactory.getLogger(javaClass)
  private val endDate = transactions.maxByOrNull { it.date }?.date
    ?: throw IllegalArgumentException("No transactions")
  private val yearsToEnd = { date: LocalDate -> ChronoUnit.DAYS.between(date, endDate).toDouble() / 365.0 }

  fun calculate(): Double {
    log.info("Starting XIRR calculation with ${transactions.size} transactions")
    log.info("End date for XIRR calculation: $endDate")

    require(transactions.size >= 2) { "Must have at least two transactions" }
    require(transactions.any { it.amount > 0 } && transactions.any { it.amount < 0 }) {
      "Need both positive and negative transactions"
    }

    // Check if all transactions are on the same day
    if (transactions.all { it.date == endDate }) {
      log.info("All transactions are on the same day. Calculating simple return.")
      val initialInvestment = -transactions.first { it.amount < 0 }.amount
      val finalValue = transactions.last { it.amount > 0 }.amount
      val simpleReturn = (finalValue - initialInvestment) / initialInvestment
      log.info("Simple return: $simpleReturn")
      return simpleReturn
    }

    val guess = transactions.sumOf { it.amount } / transactions.filter { it.amount < 0 }.sumOf { -it.amount } - 1
    log.info("Initial guess for XIRR: $guess")

    val xirrFunction = object : UnivariateDifferentiableFunction {
      override fun value(x: Double): Double {
        val result = transactions.sumOf {
          it.amount * (1 + x).pow(yearsToEnd(it.date))
        }
        log.debug("XIRR function value for x=$x: $result")
        return result
      }

      override fun value(t: DerivativeStructure): DerivativeStructure {
        val value = value(t.value)
        val derivative = transactions.sumOf {
          it.amount * yearsToEnd(it.date) * (1 + t.value).pow(yearsToEnd(it.date) - 1)
        }
        log.debug("XIRR function derivative for x=${t.value}: $derivative")
        return DerivativeStructure(t.freeParameters, t.order, value, derivative)
      }
    }

    val solver = NewtonRaphsonSolver()
    val maxEvaluations = 10000
    log.info("Solver configuration: maxEvaluations=$maxEvaluations")

    return try {
      val result = solver.solve(maxEvaluations, xirrFunction, -0.99, 0.99, guess)
      log.info("XIRR calculation result: $result")
      result
    } catch (e: Exception) {
      log.error("Error in XIRR calculation", e)
      log.error("Transactions causing error:")
      transactions.forEachIndexed { index, transaction ->
        log.error("Transaction $index: amount=${transaction.amount}, date=${transaction.date}, yearsToEnd=${yearsToEnd(transaction.date)}")
      }
      throw e
    }
  }
}
