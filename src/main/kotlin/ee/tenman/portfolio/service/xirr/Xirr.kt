package ee.tenman.portfolio.service.xirr

import org.apache.commons.math3.analysis.differentiation.DerivativeStructure
import org.apache.commons.math3.analysis.differentiation.UnivariateDifferentiableFunction
import org.apache.commons.math3.analysis.solvers.NewtonRaphsonSolver
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.pow

class Xirr(private val transactions: List<Transaction>) {
  private val endDate = try {
    transactions.maxOf { it.date }
  } catch (e: NoSuchElementException) {
    throw IllegalArgumentException("No transactions")
  }
  private val yearsToEnd = { date: LocalDate -> ChronoUnit.DAYS.between(date, endDate).toDouble() / 365.25 }

  fun calculate(): Double {
    require(transactions.size >= 2) { "Must have at least two transactions" }
    require(transactions.any { it.amount > 0 } && transactions.any { it.amount < 0 }) { "Need both positive and negative transactions" }

    val guess = estimateInitialRate()
    return NewtonRaphsonSolver().solve(1000, createXirrFunction(), guess, -1.0, 1.0)
  }

  private fun createXirrFunction(): UnivariateDifferentiableFunction {
    return object : UnivariateDifferentiableFunction {
      override fun value(x: Double): Double = netPresentValue(x)

      override fun value(t: DerivativeStructure): DerivativeStructure {
        val x = t.value
        val value = netPresentValue(x)
        val derivative = netPresentValueDerivative(x)
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
    val daysSpan = ChronoUnit.DAYS.between(transactions.minByOrNull { it.date }?.date ?: endDate, endDate).toDouble()
    return (totalAmount / totalDeposits - 1) / (daysSpan / 365.25)
  }
}
