package ee.tenman.portfolio.service

import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.TransactionType
import ee.tenman.portfolio.service.xirr.Transaction
import ee.tenman.portfolio.service.xirr.Xirr
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

@Service
class InvestmentMetricsService {
  private companion object {
    private val log = LoggerFactory.getLogger(InvestmentMetricsService::class.java)
  }

  data class InstrumentMetrics(
    val totalInvestment: BigDecimal,
    val currentValue: BigDecimal,
    val profit: BigDecimal,
    val xirr: Double
  ) {
    override fun toString(): String =
      "InstrumentMetrics(totalInvestment=$totalInvestment, " +
        "currentValue=$currentValue, " +
        "profit=$profit, " +
        "xirr=${String.format("%.2f%%", xirr * 100)})"

    companion object {
      val EMPTY = InstrumentMetrics(
        totalInvestment = BigDecimal.ZERO,
        currentValue = BigDecimal.ZERO,
        profit = BigDecimal.ZERO,
        xirr = 0.0
      )
    }
  }

  fun calculateInstrumentMetrics(
    instrument: Instrument,
    transactions: List<PortfolioTransaction>
  ): InstrumentMetrics {
    if (transactions.isEmpty()) {
      return InstrumentMetrics.EMPTY
    }

    // Group transactions by platform
    val groupedByPlatform = transactions.groupBy { it.platform }

    var totalInvestment = BigDecimal.ZERO
    var totalHoldings = BigDecimal.ZERO

    // Calculate metrics for each platform separately
    groupedByPlatform.forEach { (_, platformTransactions) ->
      val holdings = calculateCurrentHoldings(platformTransactions)
      if (holdings.quantity > BigDecimal.ZERO) {
        totalInvestment = totalInvestment.add(holdings.quantity.multiply(holdings.averageCost))
        totalHoldings = totalHoldings.add(holdings.quantity)
      }
    }

    val currentPrice = instrument.currentPrice ?: BigDecimal.ZERO
    val currentValue = totalHoldings.multiply(currentPrice)
    val profit = currentValue.subtract(totalInvestment)
    val xirr = calculateXirr(transactions, currentValue)

    return InstrumentMetrics(
      totalInvestment = totalInvestment,
      currentValue = currentValue,
      profit = profit,
      xirr = xirr
    )
  }

  private data class Holdings(
    val quantity: BigDecimal,
    val averageCost: BigDecimal
  )

  private fun calculateCurrentHoldings(transactions: List<PortfolioTransaction>): Holdings {
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
          // When selling, reduce the quantity and proportionally reduce the total cost
          val sellRatio = transaction.quantity.divide(quantity, 10, RoundingMode.HALF_UP)
          totalCost = totalCost.multiply(BigDecimal.ONE.subtract(sellRatio))
          quantity = quantity.subtract(transaction.quantity)
        }
      }
    }

    val averageCost = if (quantity > BigDecimal.ZERO) {
      totalCost.divide(quantity, 10, RoundingMode.HALF_UP)
    } else {
      BigDecimal.ZERO
    }

    return Holdings(quantity, averageCost)
  }

  private fun calculateXirr(
    transactions: List<PortfolioTransaction>,
    currentValue: BigDecimal
  ): Double {
    val xirrTransactions = buildXirrTransactions(transactions, currentValue)
    return when {
      xirrTransactions.size < 2 -> 0.0
      else -> calculateXirrSafely(xirrTransactions)
    }
  }

  private fun buildXirrTransactions(
    transactions: List<PortfolioTransaction>,
    currentValue: BigDecimal
  ): List<Transaction> {
    val cashflows = transactions.map { transaction ->
      val amount = when (transaction.transactionType) {
        TransactionType.BUY -> -(transaction.price * transaction.quantity)
        TransactionType.SELL -> transaction.price * transaction.quantity
      }
      Transaction(amount.toDouble(), transaction.transactionDate)
    }

    return if (currentValue > BigDecimal.ZERO) {
      cashflows + Transaction(currentValue.toDouble(), LocalDate.now())
    } else {
      cashflows
    }
  }

  private fun calculateXirrSafely(transactions: List<Transaction>): Double = try {
    Xirr(transactions).calculate()
  } catch (e: Exception) {
    log.error("Failed to calculate XIRR", e)
    0.0
  }
}
