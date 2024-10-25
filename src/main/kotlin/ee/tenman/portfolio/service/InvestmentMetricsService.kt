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

@Service
class InvestmentMetricsService {
  private val log = LoggerFactory.getLogger(javaClass)

  data class InstrumentMetrics(
    val totalInvestment: BigDecimal,
    val currentValue: BigDecimal,
    val profit: BigDecimal,
    val xirr: Double
  )

  fun calculateInstrumentMetrics(instrument: Instrument, transactions: List<PortfolioTransaction>): InstrumentMetrics {
    if (transactions.isEmpty()) {
      return InstrumentMetrics(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0.0)
    }

    var totalInvestment = BigDecimal.ZERO
    var quantityHeld = BigDecimal.ZERO

    // Calculate investment and holdings
    transactions.forEach { transaction ->
      val transactionAmount = transaction.price * transaction.quantity
      when (transaction.transactionType) {
        TransactionType.BUY -> {
          totalInvestment += transactionAmount
          quantityHeld += transaction.quantity
        }

        TransactionType.SELL -> {
          totalInvestment -= transactionAmount
          quantityHeld -= transaction.quantity
        }
      }
    }

    val currentPrice = instrument.currentPrice ?: BigDecimal.ZERO
    val currentValue = quantityHeld * currentPrice
    val profit = currentValue - totalInvestment

    // Calculate XIRR
    val xirrTransactions = transactions.map { transaction ->
      val amount = when (transaction.transactionType) {
        TransactionType.BUY -> -(transaction.price * transaction.quantity)
        TransactionType.SELL -> transaction.price * transaction.quantity
      }.toDouble()
      Transaction(amount, transaction.transactionDate)
    }.toMutableList()

    // Add current position as final cashflow if there are holdings
    if (quantityHeld > BigDecimal.ZERO && currentPrice > BigDecimal.ZERO) {
      xirrTransactions.add(
        Transaction(
          currentValue.toDouble(),
          LocalDate.now()
        )
      )
    }

    val xirr = if (xirrTransactions.size >= 2) {
      try {
        Xirr(xirrTransactions).calculate()
      } catch (e: Exception) {
        log.error("Failed to calculate XIRR for ${instrument.symbol}", e)
        0.0
      }
    } else {
      0.0
    }

    log.info(
      """${instrument.symbol} metrics:
            |Investment: $totalInvestment
            |Current value: $currentValue
            |Profit: $profit
            |XIRR: ${String.format("%.2f%%", xirr * 100)}""".trimMargin()
    )

    return InstrumentMetrics(
      totalInvestment = totalInvestment,
      currentValue = currentValue,
      profit = profit,
      xirr = xirr
    )
  }
}
