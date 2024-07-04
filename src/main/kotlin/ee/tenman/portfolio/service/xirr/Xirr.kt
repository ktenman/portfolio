package ee.tenman.portfolio.service.xirr

import org.apache.commons.math3.analysis.differentiation.DerivativeStructure
import org.apache.commons.math3.analysis.differentiation.UnivariateDifferentiableFunction
import org.apache.commons.math3.analysis.solvers.NewtonRaphsonSolver
import java.time.LocalDate
import java.time.temporal.ChronoUnit.DAYS
import kotlin.math.pow

class Xirr(private val transactions: List<Transaction>) {
  private val endDate = transactions.maxByOrNull { it.date }?.date
    ?: throw IllegalArgumentException("No transactions")
  private val yearsToEnd = { date: LocalDate -> DAYS.between(date, endDate).toDouble() / 365.0 }

  fun calculate(): Double {
    require(transactions.size >= 2) { "Must have at least two transactions" }
    require(transactions.any { it.amount > 0 } && transactions.any { it.amount < 0 }) {
      "Need both positive and negative transactions"
    }

    val guess = transactions.sumOf { it.amount } / transactions.filter { it.amount < 0 }.sumOf { -it.amount } - 1
    val xirrFunction = object : UnivariateDifferentiableFunction {
      override fun value(x: Double) = transactions.sumOf {
        it.amount * (1 + x).pow(yearsToEnd(it.date))
      }

      override fun value(t: DerivativeStructure) = DerivativeStructure(
        t.freeParameters, t.order, value(t.value),
        transactions.sumOf {
          it.amount * yearsToEnd(it.date) * (1 + t.value).pow(yearsToEnd(it.date) - 1)
        }
      )
    }

    return NewtonRaphsonSolver().solve(1000, xirrFunction, guess, -1.0, 1.0)
  }
}
