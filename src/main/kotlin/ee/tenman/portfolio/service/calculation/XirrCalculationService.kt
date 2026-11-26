package ee.tenman.portfolio.service.calculation

import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.TransactionType
import ee.tenman.portfolio.service.calculation.xirr.CashFlow
import ee.tenman.portfolio.service.calculation.xirr.Xirr
import ee.tenman.portfolio.service.calculation.xirr.XirrSchedule
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import kotlin.math.min

@Service
class XirrCalculationService(
  private val clock: Clock,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  companion object {
    private const val MIN_DAYS_FOR_XIRR = 0.5
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
    schedule: (List<CashFlow>) -> XirrSchedule = ::XirrSchedule,
  ): Double? {
    if (cashFlows.size < 2) return null
    return runCatching {
      val xirr = Xirr(cashFlows, schedule(cashFlows))
      val weightedDays = xirr.weightedInvestmentAge(calculationDate)
      if (weightedDays < MIN_DAYS_FOR_XIRR) return@runCatching null
      val dampingFactor = min(1.0, weightedDays / FULL_DAMPING_DAYS)
      xirr().coerceIn(-10.0, 10.0) * dampingFactor
    }.getOrElse {
      log.error("Error calculating adjusted XIRR", it)
      null
    }
  }

  fun convertToCashFlow(tx: PortfolioTransaction): CashFlow {
    val gross = tx.price.toDouble() * tx.quantity.toDouble()
    val commission = tx.commission.toDouble()
    val amount =
      when (tx.transactionType) {
        TransactionType.BUY -> -(gross + commission)
        TransactionType.SELL -> gross - commission
      }
    return CashFlow(amount, tx.transactionDate)
  }

  fun addCashFlows(
    cashFlowList: MutableList<CashFlow>,
    cashFlows: List<CashFlow>,
    currentValue: BigDecimal,
    date: LocalDate,
  ) {
    cashFlowList += cashFlows
    cashFlowList += CashFlow(currentValue.toDouble(), date)
  }
}
