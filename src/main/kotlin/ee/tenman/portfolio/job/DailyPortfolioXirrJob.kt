package ee.tenman.portfolio.job

import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.PortfolioDailySummary
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.TransactionType
import ee.tenman.portfolio.service.DailyPriceService
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
  private val portfolioSummaryService: PortfolioSummaryService,
  private val dailyPriceService: DailyPriceService
) {
  private val log = LoggerFactory.getLogger(javaClass)

  @Scheduled(cron = "0 0 1 * * ?")
  fun calculateDailyPortfolioXirr() {
    log.info("Starting daily portfolio XIRR calculation")
    val transactions = portfolioTransactionService.getAllTransactions()

    if (transactions.isEmpty()) {
      log.info("No transactions found. Skipping XIRR calculation.")
      return
    }

    try {
      val (totalInvestment, currentValue) = processTransactions(transactions)
      val xirrResult = calculateXirr(transactions, currentValue)
      saveDailySummary(totalInvestment, currentValue, xirrResult)
      log.info("Daily portfolio XIRR calculation completed. XIRR: $xirrResult")
    } catch (e: Exception) {
      log.error("Error calculating XIRR", e)
    }
  }

  private fun processTransactions(transactions: List<PortfolioTransaction>): Pair<BigDecimal, BigDecimal> {
    val (totalInvestment, holdings) = calculateInvestmentAndHoldings(transactions)
    val currentValue = calculateCurrentValue(holdings)
    return Pair(totalInvestment, currentValue)
  }

  private fun calculateInvestmentAndHoldings(transactions: List<PortfolioTransaction>): Pair<BigDecimal, Map<Instrument, BigDecimal>> {
    var totalInvestment = BigDecimal.ZERO
    val holdings = mutableMapOf<Instrument, BigDecimal>()

    transactions.forEach { transaction ->
      val amount = transaction.price * transaction.quantity
      totalInvestment += if (transaction.transactionType == TransactionType.BUY) amount else -amount

      val currentHolding = holdings.getOrDefault(transaction.instrument, BigDecimal.ZERO)
      val newHolding = when (transaction.transactionType) {
        TransactionType.BUY -> currentHolding + transaction.quantity
        TransactionType.SELL -> currentHolding - transaction.quantity
      }
      holdings[transaction.instrument] = newHolding
    }

    return Pair(totalInvestment, holdings)
  }

  private fun calculateCurrentValue(holdings: Map<Instrument, BigDecimal>): BigDecimal {
    return holdings.entries.sumOf { (instrument, quantity) ->
      val lastPrice = dailyPriceService.findLastDailyPrice(instrument)?.closePrice
        ?: throw IllegalStateException("No price found for instrument: ${instrument.symbol}")
      quantity * lastPrice
    }
  }

  private fun calculateXirr(transactions: List<PortfolioTransaction>, currentValue: BigDecimal): Double {
    val xirrTransactions = transactions.map { transaction ->
      Transaction(-transaction.price.multiply(transaction.quantity).toDouble(), transaction.transactionDate)
    }
    val finalTransaction = Transaction(currentValue.toDouble(), LocalDate.now())
    return Xirr(xirrTransactions + finalTransaction).calculate()
  }

  private fun saveDailySummary(totalInvestment: BigDecimal, currentValue: BigDecimal, xirrResult: Double) {
    PortfolioDailySummary(
      entryDate = LocalDate.now(),
      totalValue = currentValue.setScale(4, RoundingMode.HALF_UP),
      xirrAnnualReturn = BigDecimal(xirrResult).setScale(8, RoundingMode.HALF_UP),
      totalProfit = currentValue.subtract(totalInvestment).setScale(4, RoundingMode.HALF_UP),
      earningsPerDay = currentValue.multiply(BigDecimal(xirrResult))
        .divide(BigDecimal(365.25), 4, RoundingMode.HALF_UP)
    ).let { portfolioSummaryService.saveDailySummary(it) }
  }
}
