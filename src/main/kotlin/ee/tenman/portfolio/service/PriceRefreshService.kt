package ee.tenman.portfolio.service

import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.INSTRUMENT_CACHE
import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.SUMMARY_CACHE
import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.TRANSACTION_CACHE
import ee.tenman.portfolio.job.BinanceDataRetrievalJob
import ee.tenman.portfolio.job.EtfHoldingsClassificationJob
import ee.tenman.portfolio.job.LightyearHistoricalDataRetrievalJob
import ee.tenman.portfolio.job.LightyearPriceRetrievalJob
import ee.tenman.portfolio.job.WisdomTreeDataUpdateJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.springframework.cache.CacheManager
import org.springframework.stereotype.Service

@Service
class PriceRefreshService(
  private val binanceDataRetrievalJob: BinanceDataRetrievalJob?,
  private val lightyearHistoricalDataRetrievalJob: LightyearHistoricalDataRetrievalJob?,
  private val lightyearPriceRetrievalJob: LightyearPriceRetrievalJob?,
  private val etfHoldingsClassificationJob: EtfHoldingsClassificationJob?,
  private val wisdomTreeDataUpdateJob: WisdomTreeDataUpdateJob?,
  private val cacheManager: CacheManager,
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
    listOf(INSTRUMENT_CACHE, SUMMARY_CACHE, TRANSACTION_CACHE, ETF_BREAKDOWN_CACHE).forEach { cacheName ->
      cacheManager.getCache(cacheName)?.clear()
    }
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

  companion object {
    private const val ETF_BREAKDOWN_CACHE = "etf:breakdown"
  }
}
