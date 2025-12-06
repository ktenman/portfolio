package ee.tenman.portfolio.service

import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.TransactionType
import ee.tenman.portfolio.service.xirr.Transaction
import ee.tenman.portfolio.service.xirr.Xirr
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.min

@Service
class XirrCalculationService {
  private val log = LoggerFactory.getLogger(javaClass)

  fun buildXirrTransactions(
    transactions: List<PortfolioTransaction>,
    currentValue: BigDecimal,
    calculationDate: LocalDate = LocalDate.now(),
  ): List<Transaction> {
    val cashflows = transactions.map(::convertToXirrTransaction)
    val finalValue =
      currentValue
        .takeIf { it > BigDecimal.ZERO }
        ?.let { listOf(Transaction(it.toDouble(), calculationDate)) }
        ?: emptyList()
    return cashflows + finalValue
  }

  fun calculateAdjustedXirr(
    transactions: List<Transaction>,
    calculationDate: LocalDate = LocalDate.now(),
  ): Double {
    if (transactions.size < 2) return 0.0
    return runCatching {
      val xirrResult = Xirr(transactions).calculate()
      val cashFlows = transactions.filter { it.amount < 0 }
      if (cashFlows.isEmpty()) return@runCatching 0.0
      val weightedDays = calculateWeightedInvestmentAge(cashFlows, calculationDate)
      val dampingFactor = min(1.0, weightedDays / 60.0)
      xirrResult.coerceIn(-10.0, 10.0) * dampingFactor
    }.getOrElse {
      log.error("Error calculating adjusted XIRR", it)
      0.0
    }
  }

  fun convertToXirrTransaction(tx: PortfolioTransaction): Transaction {
    val amount =
      when (tx.transactionType) {
        TransactionType.BUY -> -(tx.price.multiply(tx.quantity).add(tx.commission))
        TransactionType.SELL -> tx.price.multiply(tx.quantity).subtract(tx.commission)
      }
    return Transaction(amount.toDouble(), tx.transactionDate)
  }

  fun addXirrTransactions(
    xirrList: MutableList<Transaction>,
    transactions: List<PortfolioTransaction>,
    currentValue: BigDecimal,
    date: LocalDate,
  ) {
    xirrList += transactions.map(::convertToXirrTransaction)
    xirrList += Transaction(currentValue.toDouble(), date)
  }

  private fun calculateWeightedInvestmentAge(
    cashFlows: List<Transaction>,
    calculationDate: LocalDate,
  ): Double {
    val totalInvestment = cashFlows.sumOf { -it.amount }
    return cashFlows.sumOf { transaction ->
      val weight = -transaction.amount / totalInvestment
      val days = ChronoUnit.DAYS.between(transaction.date, calculationDate).toDouble()
      days * weight
    }
  }
}
