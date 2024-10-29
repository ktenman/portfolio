package ee.tenman.portfolio.service

import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.TransactionType
import ee.tenman.portfolio.service.xirr.Transaction
import ee.tenman.portfolio.service.xirr.Xirr
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Service responsible for calculating investment metrics for financial instruments.
 */
@Service
class InvestmentMetricsService {
  private companion object {
    private val log = LoggerFactory.getLogger(InvestmentMetricsService::class.java)
  }

  /**
   * Represents the calculated metrics for a financial instrument.
   *
   * @property totalInvestment Total amount invested
   * @property currentValue Current value of the investment
   * @property profit Total profit or loss
   * @property xirr Internal rate of return
   */
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

  /**
   * Calculates investment metrics for a given instrument based on its transactions.
   *
   * @param instrument The financial instrument to analyze
   * @param transactions List of transactions for the instrument
   * @return Calculated metrics for the instrument
   */
  fun calculateInstrumentMetrics(
    instrument: Instrument,
    transactions: List<PortfolioTransaction>
  ): InstrumentMetrics {
    if (transactions.isEmpty()) {
      return InstrumentMetrics.EMPTY
    }

    val positionMetrics = calculatePositionMetrics(transactions)
    val currentValue = calculateCurrentValue(instrument.currentPrice, positionMetrics.quantityHeld)
    val profit = currentValue - positionMetrics.totalInvestment
    val xirr = calculateXirr(transactions, currentValue)

    return InstrumentMetrics(
      totalInvestment = positionMetrics.totalInvestment,
      currentValue = currentValue,
      profit = profit,
      xirr = xirr
    ).also {
      log.info("Calculated metrics for ${instrument.symbol}: $it")
    }
  }

  private data class PositionMetrics(
    val totalInvestment: BigDecimal,
    val quantityHeld: BigDecimal
  )

  private fun calculatePositionMetrics(
    transactions: List<PortfolioTransaction>
  ): PositionMetrics = transactions.fold(
    PositionMetrics(BigDecimal.ZERO, BigDecimal.ZERO)
  ) { accumulatedMetrics, transaction ->
    val transactionAmount = transaction.price * transaction.quantity
    when (transaction.transactionType) {
      TransactionType.BUY -> PositionMetrics(
        totalInvestment = accumulatedMetrics.totalInvestment + transactionAmount,
        quantityHeld = accumulatedMetrics.quantityHeld + transaction.quantity
      )
      TransactionType.SELL -> PositionMetrics(
        totalInvestment = accumulatedMetrics.totalInvestment - transactionAmount,
        quantityHeld = accumulatedMetrics.quantityHeld - transaction.quantity
      )
    }
  }

  private fun calculateCurrentValue(
    currentPrice: BigDecimal?,
    quantityHeld: BigDecimal
  ): BigDecimal = (currentPrice ?: BigDecimal.ZERO) * quantityHeld

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
    val baseTransactions = transactions.map { transaction ->
      val amount = when (transaction.transactionType) {
        TransactionType.BUY -> -(transaction.price * transaction.quantity)
        TransactionType.SELL -> transaction.price * transaction.quantity
      }
      Transaction(amount.toDouble(), transaction.transactionDate)
    }

    return if (currentValue > BigDecimal.ZERO) {
      baseTransactions + Transaction(currentValue.toDouble(), LocalDate.now())
    } else {
      baseTransactions
    }
  }

  private fun calculateXirrSafely(transactions: List<Transaction>): Double = try {
    Xirr(transactions).calculate()
  } catch (e: Exception) {
    log.error("Failed to calculate XIRR", e)
    0.0
  }
}
