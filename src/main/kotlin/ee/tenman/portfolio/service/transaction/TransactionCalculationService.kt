package ee.tenman.portfolio.service.transaction

import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.TransactionType
import ee.tenman.portfolio.repository.PortfolioTransactionRepository
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class TransactionCalculationService(
  private val transactionRepository: PortfolioTransactionRepository,
) {
  fun calculateNetQuantity(
    instrumentId: Long,
    platformFilter: Set<Platform>? = null,
  ): BigDecimal = calculateNetQuantityFromTransactions(getFilteredTransactions(instrumentId, platformFilter))

  fun getPlatformsForInstrument(
    instrumentId: Long,
    platformFilter: Set<Platform>? = null,
  ): Set<Platform> = getFilteredTransactions(instrumentId, platformFilter).map { it.platform }.toSet()

  private fun getFilteredTransactions(
    instrumentId: Long,
    platformFilter: Set<Platform>?,
  ): List<PortfolioTransaction> {
    val transactions = transactionRepository.findAllByInstrumentId(instrumentId)
    return platformFilter?.let { filter -> transactions.filter { it.platform in filter } } ?: transactions
  }

  fun batchCalculateAll(
    instrumentIds: Collection<Long>,
    platformFilter: Set<Platform>? = null,
  ): Map<Long, InstrumentTransactionData> {
    if (instrumentIds.isEmpty()) return emptyMap()
    val allTransactions = loadTransactions(instrumentIds, platformFilter)
    return instrumentIds.associateWith { id ->
      val txs = allTransactions.filter { it.instrument.id == id }
      val quantityByPlatform =
        txs.groupBy { it.platform }.mapValues { (_, platformTxs) ->
        calculateNetQuantityFromTransactions(platformTxs)
      }
      InstrumentTransactionData(
        netQuantity = calculateNetQuantityFromTransactions(txs),
        platforms = txs.map { it.platform }.toSet(),
        quantityByPlatform = quantityByPlatform,
      )
    }
  }

  private fun loadTransactions(
    instrumentIds: Collection<Long>,
    platformFilter: Set<Platform>?,
  ): List<PortfolioTransaction> =
    platformFilter
      ?.let { transactionRepository.findAllByPlatformsAndInstrumentIds(it.toList(), instrumentIds.toList()) }
      ?: transactionRepository.findAllByInstrumentIds(instrumentIds.toList())

  fun getTransactionStats(instrumentId: Long): TransactionStats {
    val transactions = transactionRepository.findAllByInstrumentId(instrumentId)
    return TransactionStats(
      count = transactions.size,
      platforms = transactions.map { it.platform.name }.distinct(),
    )
  }

  private fun calculateNetQuantityFromTransactions(transactions: List<PortfolioTransaction>): BigDecimal =
    transactions.fold(BigDecimal.ZERO) { acc, tx ->
      when (tx.transactionType) {
        TransactionType.BUY -> acc.add(tx.quantity)
        TransactionType.SELL -> acc.subtract(tx.quantity)
      }
    }
}
