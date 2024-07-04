package ee.tenman.portfolio.job

import ee.tenman.portfolio.domain.PortfolioDailySummary
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.TransactionType
import ee.tenman.portfolio.service.PortfolioSummaryService
import ee.tenman.portfolio.service.PortfolioTransactionService
import ee.tenman.portfolio.service.xirr.Transaction
import ee.tenman.portfolio.service.xirr.Xirr
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

@Component
class DailyPortfolioXirrJob(
  private val portfolioTransactionService: PortfolioTransactionService,
  private val portfolioSummaryService: PortfolioSummaryService
) {
  companion object {
    private val log = LoggerFactory.getLogger(DailyPortfolioXirrJob::class.java)
  }

  @Scheduled(cron = "0 0 1 * * ?") // Runs at 1:00 AM every day
  fun calculateDailyPortfolioXirr() {
    log.info("Starting daily portfolio XIRR calculation")
    val today = LocalDate.now()
    val transactions = portfolioTransactionService.getAllTransactions()

    val xirrTransactions = transactions.map { transaction ->
      Transaction(
        -transaction.price.multiply(transaction.quantity).toDouble(),
        transaction.transactionDate
      )
    }

    // Add the current portfolio value as a positive cash flow
    val currentPortfolioValue = calculateCurrentPortfolioValue(transactions)
    xirrTransactions.plus(Transaction(currentPortfolioValue.toDouble(), today))

    val xirr = Xirr(xirrTransactions).calculate()
    val totalProfit = currentPortfolioValue.subtract(calculateTotalInvestment(transactions))
    val earningsPerDay = (xirr * currentPortfolioValue.toDouble() / 365).toBigDecimal()

    val portfolioDailySummary = PortfolioDailySummary(
      entryDate = today,
      totalValue = currentPortfolioValue,
      xirrAnnualReturn = BigDecimal(xirr).setScale(8, RoundingMode.HALF_UP),
      totalProfit = totalProfit,
      earningsPerDay = earningsPerDay.setScale(2, RoundingMode.HALF_UP)
    )

    portfolioSummaryService.saveDailySummary(portfolioDailySummary)
    log.info("Completed daily portfolio XIRR calculation")
  }

  private fun calculateCurrentPortfolioValue(transactions: List<PortfolioTransaction>): BigDecimal {
    // This is a simplified calculation. In a real-world scenario, you'd fetch current market prices.
    return transactions.groupBy { it.instrument }
      .map { (instrument, instrumentTransactions) ->
        val quantity = instrumentTransactions.sumOf {
          if (it.transactionType == TransactionType.BUY) it.quantity else it.quantity.negate()
        }
        val lastPrice = instrumentTransactions.maxByOrNull { it.transactionDate }?.price ?: BigDecimal.ZERO
        quantity.multiply(lastPrice)
      }
      .sumOf { it }
  }

  private fun calculateTotalInvestment(transactions: List<PortfolioTransaction>): BigDecimal {
    return transactions.sumOf {
      if (it.transactionType == TransactionType.BUY)
        it.price.multiply(it.quantity)
      else
        it.price.multiply(it.quantity).negate()
    }
  }
}
