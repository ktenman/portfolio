package ee.tenman.portfolio.service

import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.TransactionType
import ee.tenman.portfolio.dto.TransactionResponseDto
import ee.tenman.portfolio.dto.TransactionSummaryDto
import ee.tenman.portfolio.dto.TransactionsWithSummaryDto
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate

@Service
class TransactionQueryService(
  private val transactionService: TransactionService,
  private val investmentMetricsService: InvestmentMetricsService,
) {
  @Transactional(readOnly = true)
  fun getTransactionsWithSummary(
    platforms: List<String>?,
    fromDate: LocalDate?,
    untilDate: LocalDate?,
  ): TransactionsWithSummaryDto {
    val transactions = transactionService.getAllTransactions(platforms, fromDate, untilDate)
    val isDateFiltered = fromDate != null || untilDate != null
    calculateProfitsForTransactions(transactions, platforms, isDateFiltered)
    val summary = calculateTransactionSummary(transactions)
    return TransactionsWithSummaryDto(
      transactions = transactions.map { TransactionResponseDto.fromEntity(it) },
      summary = summary,
    )
  }

  @Transactional(readOnly = true)
  fun getTransactionWithProfits(id: Long): TransactionResponseDto {
    val transaction = transactionService.getTransactionById(id)
    transactionService.calculateTransactionProfits(listOf(transaction))
    return TransactionResponseDto.fromEntity(transaction)
  }

  private fun calculateProfitsForTransactions(
    transactions: List<PortfolioTransaction>,
    platforms: List<String>?,
    isDateFiltered: Boolean,
  ) {
    if (transactions.isEmpty()) return
    when {
      isDateFiltered -> calculateProfitsWithFullHistory(transactions, platforms)
      else -> transactionService.calculateTransactionProfits(transactions)
    }
  }

  private fun calculateProfitsWithFullHistory(
    transactions: List<PortfolioTransaction>,
    platforms: List<String>?,
  ) {
    val fullHistory = transactionService.getFullTransactionHistoryForProfitCalculation(transactions, platforms)
    transactionService.calculateTransactionProfits(fullHistory)
    val profitMap = fullHistory.associateBy { it.id }
    transactions.forEach { tx ->
      profitMap[tx.id]?.let { calculated ->
        tx.realizedProfit = calculated.realizedProfit
        tx.unrealizedProfit = calculated.unrealizedProfit
        tx.remainingQuantity = calculated.remainingQuantity
        tx.averageCost = calculated.averageCost
      }
    }
  }

  private fun calculateTransactionSummary(transactions: List<PortfolioTransaction>): TransactionSummaryDto {
    val totalUnrealizedProfit = calculateTotalUnrealizedProfit(transactions)
    val totalRealizedProfit = calculateTotalRealizedProfit(transactions)
    val totalInvested = calculateTotalInvested(transactions)
    return TransactionSummaryDto(
      totalRealizedProfit = totalRealizedProfit,
      totalUnrealizedProfit = totalUnrealizedProfit,
      totalProfit = totalRealizedProfit + totalUnrealizedProfit,
      totalInvested = totalInvested,
    )
  }

  private fun calculateTotalUnrealizedProfit(transactions: List<PortfolioTransaction>): BigDecimal =
    transactions
      .groupBy { it.instrument }
      .entries
      .sumOf { (instrument, instrumentTransactions) ->
        investmentMetricsService.calculateInstrumentMetrics(instrument, instrumentTransactions).unrealizedProfit
      }

  private fun calculateTotalRealizedProfit(transactions: List<PortfolioTransaction>): BigDecimal =
    transactions
      .filter { it.transactionType == TransactionType.SELL }
      .sumOf { it.realizedProfit ?: BigDecimal.ZERO }

  private fun calculateTotalInvested(transactions: List<PortfolioTransaction>): BigDecimal =
    transactions.sumOf { transaction ->
      val transactionCost = transaction.price.multiply(transaction.quantity).add(transaction.commission)
      when (transaction.transactionType) {
        TransactionType.BUY -> transactionCost
        TransactionType.SELL -> transactionCost.negate()
      }
    }
}
