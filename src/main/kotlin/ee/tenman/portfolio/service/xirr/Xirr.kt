package ee.tenman.portfolio.service.xirr

import org.apache.commons.math3.analysis.differentiation.DerivativeStructure
import org.apache.commons.math3.analysis.differentiation.UnivariateDifferentiableFunction
import org.apache.commons.math3.analysis.solvers.NewtonRaphsonSolver
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.pow

class Xirr(transactions: Collection<Transaction>?) {
  private val investments: List<Investment>
  private val details: XirrDetails
  private val solver = NewtonRaphsonSolver()
  private var guess: Double? = null

  init {
    require(!(transactions == null || transactions.size < 2)) { "Must have at least two transactions" }
    this.details = XirrDetails(transactions)
    this.investments = transactions.stream()
      .map { t: Transaction -> Investment(t, details.end) }
      .toList()
  }

  fun xirr(): Double {
    if (details.maxAmount == 0.0) {
      return (-1).toDouble()
    }
    this.guess =
      if ((this.guess != null)) this.guess else (details.total / details.deposits) / (ChronoUnit.DAYS.between(
        details.start,
        details.end
      ) / DAYS_IN_YEAR)
    val xirrFunction = this.createXirrFunction()
    return solver.solve(1000, xirrFunction, guess!!, -1.0, 1.0)
  }

  private fun createXirrFunction(): UnivariateDifferentiableFunction {
    return object : UnivariateDifferentiableFunction {
      override fun value(rate: Double): Double {
        return investments.stream().mapToDouble { investment: Investment -> investment.presentValue(rate) }
          .sum()
      }

      override fun value(t: DerivativeStructure): DerivativeStructure {
        val rate = t.value
        return DerivativeStructure(
          t.freeParameters, t.order, this.value(rate),
          investments.stream().mapToDouble { inv: Investment -> inv.derivative(rate) }.sum()
        )
      }
    }
  }

  private class Investment(transaction: Transaction, endDate: LocalDate?) {
    val amount: Double = transaction.amount
    val years: Double = ChronoUnit.DAYS.between(transaction.date, endDate) / DAYS_IN_YEAR

    fun presentValue(rate: Double): Double {
      return if (this.years == 0.0) this.amount else this.amount * (1 + rate).pow(this.years)
    }

    fun derivative(rate: Double): Double {
      return if (this.years == 0.0) 0.0 else this.amount * this.years * (1 + rate).pow(this.years - 1)
    }
  }

  companion object {
    private const val DAYS_IN_YEAR = 365.25
  }
}
