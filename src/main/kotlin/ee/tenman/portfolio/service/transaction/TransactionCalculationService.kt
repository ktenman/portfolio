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
  ): BigDecimal {
    var transactions = transactionRepository.findAllByInstrumentId(instrumentId)
    if (platformFilter != null) transactions = transactions.filter { platformFilter.contains(it.platform) }
    return calculateNetQuantityFromTransactions(transactions)
  }

  fun getPlatformsForInstrument(
    instrumentId: Long,
    platformFilter: Set<Platform>? = null,
  ): Set<Platform> {
    var transactions = transactionRepository.findAllByInstrumentId(instrumentId)
    if (platformFilter != null) transactions = transactions.filter { platformFilter.contains(it.platform) }
    return transactions.map { it.platform }.toSet()
  }

  fun batchCalculateNetQuantities(instrumentIds: Collection<Long>): Map<Long, BigDecimal> {
    if (instrumentIds.isEmpty()) return emptyMap()
    val allTransactions = transactionRepository.findAllByInstrumentIds(instrumentIds.toList())
    return instrumentIds.associateWith { id ->
      val txs = allTransactions.filter { it.instrument.id == id }
      calculateNetQuantityFromTransactions(txs)
    }
  }

  fun batchGetPlatforms(instrumentIds: Collection<Long>): Map<Long, Set<Platform>> {
    if (instrumentIds.isEmpty()) return emptyMap()
    val allTransactions = transactionRepository.findAllByInstrumentIds(instrumentIds.toList())
    return instrumentIds.associateWith { id ->
      allTransactions.filter { it.instrument.id == id }.map { it.platform }.toSet()
    }
  }

  fun batchCalculateAll(instrumentIds: Collection<Long>): Map<Long, InstrumentTransactionData> {
    if (instrumentIds.isEmpty()) return emptyMap()
    val allTransactions = transactionRepository.findAllByInstrumentIds(instrumentIds.toList())
    return instrumentIds.associateWith { id ->
      val txs = allTransactions.filter { it.instrument.id == id }
      InstrumentTransactionData(
        netQuantity = calculateNetQuantityFromTransactions(txs),
        platforms = txs.map { it.platform }.toSet(),
      )
    }
  }

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
