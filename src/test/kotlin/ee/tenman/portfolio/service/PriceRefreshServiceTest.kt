package ee.tenman.portfolio.service

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.INSTRUMENT_CACHE
import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.SUMMARY_CACHE
import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.TRANSACTION_CACHE
import ee.tenman.portfolio.job.BinanceDataRetrievalJob
import ee.tenman.portfolio.job.EtfHoldingsClassificationJob
import ee.tenman.portfolio.job.LightyearHistoricalDataRetrievalJob
import ee.tenman.portfolio.job.LightyearPriceRetrievalJob
import ee.tenman.portfolio.job.WisdomTreeDataUpdateJob
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.cache.Cache
import org.springframework.cache.CacheManager

class PriceRefreshServiceTest {
  private val binanceDataRetrievalJob = mockk<BinanceDataRetrievalJob>(relaxed = true)
  private val lightyearHistoricalDataRetrievalJob = mockk<LightyearHistoricalDataRetrievalJob>(relaxed = true)
  private val lightyearPriceRetrievalJob = mockk<LightyearPriceRetrievalJob>(relaxed = true)
  private val etfHoldingsClassificationJob = mockk<EtfHoldingsClassificationJob>(relaxed = true)
  private val wisdomTreeDataUpdateJob = mockk<WisdomTreeDataUpdateJob>(relaxed = true)
  private val cacheManager = mockk<CacheManager>()
  private val transactionService = mockk<TransactionService>()

  private lateinit var priceRefreshService: PriceRefreshService

  @BeforeEach
  fun setup() {
    val mockCache = mockk<Cache>(relaxed = true)
    every { cacheManager.getCache(any()) } returns mockCache
    every { transactionService.getAllTransactions() } returns emptyList()
    coEvery { transactionService.calculateTransactionProfits(any()) } returns Unit

    priceRefreshService =
      PriceRefreshService(
        binanceDataRetrievalJob = binanceDataRetrievalJob,
        lightyearHistoricalDataRetrievalJob = lightyearHistoricalDataRetrievalJob,
        lightyearPriceRetrievalJob = lightyearPriceRetrievalJob,
        etfHoldingsClassificationJob = etfHoldingsClassificationJob,
        wisdomTreeDataUpdateJob = wisdomTreeDataUpdateJob,
        cacheManager = cacheManager,
        transactionService = transactionService,
      )
  }

  @Test
  fun `refreshAllPrices should return success message`() {
    val result = priceRefreshService.refreshAllPrices()

    expect(result).toEqual("Jobs triggered, caches cleared, and transaction profits recalculated")
  }

  @Test
  fun `refreshAllPrices should clear all caches`() {
    priceRefreshService.refreshAllPrices()

    verify { cacheManager.getCache(INSTRUMENT_CACHE) }
    verify { cacheManager.getCache(SUMMARY_CACHE) }
    verify { cacheManager.getCache(TRANSACTION_CACHE) }
    verify { cacheManager.getCache("etf:breakdown") }
  }

  @Test
  fun `triggerEtfHoldingsClassification should return success when job available`() {
    val result = priceRefreshService.triggerEtfHoldingsClassification()

    expect(result).toEqual("ETF holdings classification job triggered")
  }

  @Test
  fun `triggerEtfHoldingsClassification should return not available when job is null`() {
    val serviceWithoutJob =
      PriceRefreshService(
        binanceDataRetrievalJob = null,
        lightyearHistoricalDataRetrievalJob = null,
        lightyearPriceRetrievalJob = null,
        etfHoldingsClassificationJob = null,
        wisdomTreeDataUpdateJob = null,
        cacheManager = cacheManager,
        transactionService = transactionService,
      )

    val result = serviceWithoutJob.triggerEtfHoldingsClassification()

    expect(result).toEqual("ETF holdings classification job not available")
  }

  @Test
  fun `triggerWisdomTreeDataUpdate should return success when job available`() {
    val result = priceRefreshService.triggerWisdomTreeDataUpdate()

    expect(result).toEqual("WisdomTree data update job triggered")
  }

  @Test
  fun `triggerWisdomTreeDataUpdate should return not available when job is null`() {
    val serviceWithoutJob =
      PriceRefreshService(
        binanceDataRetrievalJob = null,
        lightyearHistoricalDataRetrievalJob = null,
        lightyearPriceRetrievalJob = null,
        etfHoldingsClassificationJob = null,
        wisdomTreeDataUpdateJob = null,
        cacheManager = cacheManager,
        transactionService = transactionService,
      )

    val result = serviceWithoutJob.triggerWisdomTreeDataUpdate()

    expect(result).toEqual("WisdomTree data update job not available")
  }

  @Test
  fun `refreshAllPrices should work when all jobs are null`() {
    val serviceWithoutJobs =
      PriceRefreshService(
        binanceDataRetrievalJob = null,
        lightyearHistoricalDataRetrievalJob = null,
        lightyearPriceRetrievalJob = null,
        etfHoldingsClassificationJob = null,
        wisdomTreeDataUpdateJob = null,
        cacheManager = cacheManager,
        transactionService = transactionService,
      )

    val result = serviceWithoutJobs.refreshAllPrices()

    expect(result).toEqual("Jobs triggered, caches cleared, and transaction profits recalculated")
  }
}
