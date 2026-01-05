package ee.tenman.portfolio.service.pricing

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.job.BinanceDataRetrievalJob
import ee.tenman.portfolio.job.EtfHoldingsClassificationJob
import ee.tenman.portfolio.job.LightyearHistoricalDataRetrievalJob
import ee.tenman.portfolio.job.LightyearPriceRetrievalJob
import ee.tenman.portfolio.service.infrastructure.CacheInvalidationService
import ee.tenman.portfolio.service.transaction.TransactionService
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PriceRefreshServiceTest {
  private val binanceDataRetrievalJob = mockk<BinanceDataRetrievalJob>(relaxed = true)
  private val lightyearHistoricalDataRetrievalJob = mockk<LightyearHistoricalDataRetrievalJob>(relaxed = true)
  private val lightyearPriceRetrievalJob = mockk<LightyearPriceRetrievalJob>(relaxed = true)
  private val etfHoldingsClassificationJob = mockk<EtfHoldingsClassificationJob>(relaxed = true)
  private val cacheInvalidationService = mockk<CacheInvalidationService>(relaxed = true)
  private val transactionService = mockk<TransactionService>()

  private lateinit var priceRefreshService: PriceRefreshService

  @BeforeEach
  fun setup() {
    every { transactionService.getAllTransactions() } returns emptyList()
    coEvery { transactionService.calculateTransactionProfits(any()) } returns Unit

    priceRefreshService =
      PriceRefreshService(
        binanceDataRetrievalJob = binanceDataRetrievalJob,
        lightyearHistoricalDataRetrievalJob = lightyearHistoricalDataRetrievalJob,
        lightyearPriceRetrievalJob = lightyearPriceRetrievalJob,
        etfHoldingsClassificationJob = etfHoldingsClassificationJob,
        cacheInvalidationService = cacheInvalidationService,
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

    verify { cacheInvalidationService.evictAllRelatedCaches(null, null) }
    verify { cacheInvalidationService.evictEtfBreakdownCache() }
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
        cacheInvalidationService = cacheInvalidationService,
        transactionService = transactionService,
      )

    val result = serviceWithoutJob.triggerEtfHoldingsClassification()

    expect(result).toEqual("ETF holdings classification job not available")
  }

  @Test
  fun `refreshAllPrices should work when all jobs are null`() {
    val serviceWithoutJobs =
      PriceRefreshService(
        binanceDataRetrievalJob = null,
        lightyearHistoricalDataRetrievalJob = null,
        lightyearPriceRetrievalJob = null,
        etfHoldingsClassificationJob = null,
        cacheInvalidationService = cacheInvalidationService,
        transactionService = transactionService,
      )

    val result = serviceWithoutJobs.refreshAllPrices()

    expect(result).toEqual("Jobs triggered, caches cleared, and transaction profits recalculated")
  }
}
