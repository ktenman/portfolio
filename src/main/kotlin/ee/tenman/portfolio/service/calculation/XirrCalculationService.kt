package ee.tenman.portfolio.service.calculation

import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.TransactionType
import ee.tenman.portfolio.service.calculation.xirr.CashFlow
import ee.tenman.portfolio.service.calculation.xirr.Xirr
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.min

@Service
class XirrCalculationService(
  private val clock: Clock,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  companion object {
    private const val MIN_DAYS_FOR_XIRR = 2.0
    private const val FULL_DAMPING_DAYS = 365.25 / 6
  }

  fun buildCashFlows(
    transactions: List<PortfolioTransaction>,
    currentValue: BigDecimal,
    calculationDate: LocalDate = LocalDate.now(clock),
  ): List<CashFlow> {
    val cashFlows = transactions.map(::convertToCashFlow)
    val finalValue =
      currentValue
        .takeIf { it > BigDecimal.ZERO }
        ?.let { listOf(CashFlow(it.toDouble(), calculationDate)) }
        ?: emptyList()
    return cashFlows + finalValue
  }

  fun calculateAdjustedXirr(
    cashFlows: List<CashFlow>,
    calculationDate: LocalDate = LocalDate.now(clock),
  ): Double? {
    if (cashFlows.size < 2) return null
    return runCatching {
      val outflows = cashFlows.filter { it.amount < 0 }
      if (outflows.isEmpty()) return@runCatching null
      val weightedDays = calculateWeightedInvestmentAge(outflows, calculationDate)
      if (weightedDays < MIN_DAYS_FOR_XIRR) return@runCatching null
      val xirrResult = Xirr(cashFlows)()
      val dampingFactor = min(1.0, weightedDays / FULL_DAMPING_DAYS)
      xirrResult.coerceIn(-10.0, 10.0) * dampingFactor
    }.getOrElse {
      log.error("Error calculating adjusted XIRR", it)
      null
    }
  }

  fun convertToCashFlow(tx: PortfolioTransaction): CashFlow {
    val amount =
      when (tx.transactionType) {
        TransactionType.BUY -> -(tx.price.multiply(tx.quantity).add(tx.commission))
        TransactionType.SELL -> tx.price.multiply(tx.quantity).subtract(tx.commission)
      }
    return CashFlow(amount.toDouble(), tx.transactionDate)
  }

  fun addCashFlows(
    cashFlowList: MutableList<CashFlow>,
    transactions: List<PortfolioTransaction>,
    currentValue: BigDecimal,
    date: LocalDate,
  ) {
    cashFlowList += transactions.map(::convertToCashFlow)
    cashFlowList += CashFlow(currentValue.toDouble(), date)
  }

  private fun calculateWeightedInvestmentAge(
    cashFlows: List<CashFlow>,
    calculationDate: LocalDate,
  ): Double {
    val totalInvestment = cashFlows.sumOf { -it.amount }
    return cashFlows.sumOf { cashFlow ->
      val weight = -cashFlow.amount / totalInvestment
      val days = ChronoUnit.DAYS.between(cashFlow.date, calculationDate).toDouble()
      days * weight
    }
  }
}
