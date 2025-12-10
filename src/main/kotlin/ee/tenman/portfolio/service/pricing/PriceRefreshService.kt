package ee.tenman.portfolio.service.pricing

import ee.tenman.portfolio.job.BinanceDataRetrievalJob
import ee.tenman.portfolio.job.EtfHoldingsClassificationJob
import ee.tenman.portfolio.job.LightyearHistoricalDataRetrievalJob
import ee.tenman.portfolio.job.LightyearPriceRetrievalJob
import ee.tenman.portfolio.job.WisdomTreeDataUpdateJob
import ee.tenman.portfolio.service.infrastructure.CacheInvalidationService
import ee.tenman.portfolio.service.transaction.TransactionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.springframework.stereotype.Service

@Service
class PriceRefreshService(
  private val binanceDataRetrievalJob: BinanceDataRetrievalJob?,
  private val lightyearHistoricalDataRetrievalJob: LightyearHistoricalDataRetrievalJob?,
  private val lightyearPriceRetrievalJob: LightyearPriceRetrievalJob?,
  private val etfHoldingsClassificationJob: EtfHoldingsClassificationJob?,
  private val wisdomTreeDataUpdateJob: WisdomTreeDataUpdateJob?,
  private val cacheInvalidationService: CacheInvalidationService,
  private val transactionService: TransactionService,
) {
  fun refreshAllPrices(): String {
    triggerPriceRetrievalJobs()
    clearAllCaches()
    recalculateTransactionProfits()
    return "Jobs triggered, caches cleared, and transaction profits recalculated"
  }

  fun triggerEtfHoldingsClassification(): String {
    etfHoldingsClassificationJob?.let { job ->
      launchJob { job.execute() }
      return "ETF holdings classification job triggered"
    }
    return "ETF holdings classification job not available"
  }

  fun triggerWisdomTreeDataUpdate(): String {
    wisdomTreeDataUpdateJob?.let { job ->
      launchJob { job.execute() }
      return "WisdomTree data update job triggered"
    }
    return "WisdomTree data update job not available"
  }

  private fun triggerPriceRetrievalJobs() {
    binanceDataRetrievalJob?.let { job -> launchJob { job.execute() } }
    lightyearHistoricalDataRetrievalJob?.let { job -> launchJob { job.execute() } }
    lightyearPriceRetrievalJob?.let { job -> launchJob { job.execute() } }
  }

  private fun clearAllCaches() {
    cacheInvalidationService.evictAllRelatedCaches(null, null)
    cacheInvalidationService.evictEtfBreakdownCache()
  }

  private fun recalculateTransactionProfits() {
    launchJob {
      val allTransactions = transactionService.getAllTransactions()
      transactionService.calculateTransactionProfits(allTransactions)
    }
  }

  private fun launchJob(block: () -> Unit) {
    CoroutineScope(Dispatchers.Default).launch { block() }
  }
}
