package ee.tenman.portfolio.service.calculation

import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.TransactionType
import ee.tenman.portfolio.service.calculation.xirr.CashFlow
import ee.tenman.portfolio.service.calculation.xirr.Xirr
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.min

@Service
class XirrCalculationService {
  private val log = LoggerFactory.getLogger(javaClass)

  fun buildCashFlows(
    transactions: List<PortfolioTransaction>,
    currentValue: BigDecimal,
    calculationDate: LocalDate = LocalDate.now(),
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
    calculationDate: LocalDate = LocalDate.now(),
  ): Double {
    if (cashFlows.size < 2) return 0.0
    return runCatching {
      val xirrResult = Xirr(cashFlows)()
      val outflows = cashFlows.filter { it.amount < 0 }
      if (outflows.isEmpty()) return@runCatching 0.0
      val weightedDays = calculateWeightedInvestmentAge(outflows, calculationDate)
      val dampingFactor = min(1.0, weightedDays / 60.0)
      xirrResult.coerceIn(-10.0, 10.0) * dampingFactor
    }.getOrElse {
      log.error("Error calculating adjusted XIRR", it)
      0.0
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
