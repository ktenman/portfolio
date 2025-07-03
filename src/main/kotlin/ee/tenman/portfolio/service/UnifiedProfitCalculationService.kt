package ee.tenman.portfolio.service

import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.TransactionType
import ee.tenman.portfolio.service.xirr.Transaction
import ee.tenman.portfolio.service.xirr.Xirr
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.min

@Service
class UnifiedProfitCalculationService {
  private val log = LoggerFactory.getLogger(javaClass)

  /**
   * Calculates profit based on holdings, cost basis, and current price
   */
  fun calculateProfit(
    holdings: BigDecimal,
    averageCost: BigDecimal,
    currentPrice: BigDecimal,
  ): BigDecimal {
    val currentValue = holdings.multiply(currentPrice)
    val investment = holdings.multiply(averageCost)
    return currentValue.subtract(investment)
  }

  /**
   * Calculates current value of holdings at a given price
   */
  fun calculateCurrentValue(
    holdings: BigDecimal,
    currentPrice: BigDecimal,
  ): BigDecimal = holdings.multiply(currentPrice)

  fun calculateAdjustedXirr(
    transactions: List<Transaction>,
    currentValue: BigDecimal,
    calculationDate: LocalDate = LocalDate.now(), // Default for backward compatibility
  ): Double {
    if (transactions.size < 2) {
      return 0.0
    }

    try {
      val xirrResult = Xirr(transactions).calculate()

      // Calculate weighted age of investments in days
      val cashFlows = transactions.filter { it.amount < 0 }
      if (cashFlows.isEmpty()) {
        return 0.0
      }

      val totalInvestment = cashFlows.sumOf { -it.amount }
      val weightedDays =
        cashFlows.sumOf { transaction ->
          val weight = -transaction.amount / totalInvestment
          // Use the provided calculation date instead of LocalDate.now()
          val days = ChronoUnit.DAYS.between(transaction.date, calculationDate).toDouble()
          days * weight
        }

      // Apply dampening for very recent investments
      val dampingFactor = min(1.0, weightedDays / 60.0)
      val boundedXirr = xirrResult.coerceIn(-10.0, 10.0)

      return boundedXirr * dampingFactor
    } catch (e: Exception) {
      log.error("Error calculating adjusted XIRR", e)
      return 0.0
    }
  }

  /**
   * Calculates current holdings based on a list of transactions
   * @return Pair of (quantity, averageCost)
   */
  fun calculateCurrentHoldings(transactions: List<PortfolioTransaction>): Pair<BigDecimal, BigDecimal> {
    var quantity = BigDecimal.ZERO
    var totalCost = BigDecimal.ZERO

    transactions.sortedBy { it.transactionDate }.forEach { transaction ->
      when (transaction.transactionType) {
        TransactionType.BUY -> {
          val cost = transaction.price.multiply(transaction.quantity)
          totalCost = totalCost.add(cost)
          quantity = quantity.add(transaction.quantity)
        }

        TransactionType.SELL -> {
          if (quantity.compareTo(BigDecimal.ZERO) > 0) {
            val sellRatio = transaction.quantity.divide(quantity, 10, RoundingMode.HALF_UP)
            totalCost = totalCost.multiply(BigDecimal.ONE.subtract(sellRatio))
            quantity = quantity.subtract(transaction.quantity)
          }
        }
      }
    }

    val averageCost =
      if (quantity.compareTo(BigDecimal.ZERO) > 0) {
        totalCost.divide(quantity, 10, RoundingMode.HALF_UP)
      } else {
        BigDecimal.ZERO
      }

    return Pair(quantity, averageCost)
  }

  /**
   * Builds transactions list for XIRR calculation
   */
  fun buildXirrTransactions(
    transactions: List<PortfolioTransaction>,
    currentValue: BigDecimal,
    calculationDate: LocalDate = LocalDate.now(),
  ): List<Transaction> {
    val cashflows =
      transactions.map { transaction ->
        val amount =
          when (transaction.transactionType) {
            TransactionType.BUY -> -(transaction.price * transaction.quantity)
            TransactionType.SELL -> transaction.price * transaction.quantity
          }
        Transaction(amount.toDouble(), transaction.transactionDate)
      }

    return if (currentValue > BigDecimal.ZERO) {
      cashflows + Transaction(currentValue.toDouble(), calculationDate)
    } else {
      cashflows
    }
  }
}
