package ee.tenman.portfolio.service.summary

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioDailySummary
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.TimeRange
import ee.tenman.portfolio.domain.TransactionType
import ee.tenman.portfolio.repository.PortfolioDailySummaryRepository
import ee.tenman.portfolio.service.transaction.TransactionService
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.cache.CacheManager
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

class SummaryServiceSeriesTest {
  private val today = LocalDate.of(2026, 8, 14)
  private val clock = Clock.fixed(today.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC)
  private val repository = mockk<PortfolioDailySummaryRepository>()
  private val transactionService = mockk<TransactionService>()
  private val batchProcessor = mockk<SummaryBatchProcessorService>()
  private val summaryCacheService = mockk<SummaryCacheService>()

  private val service =
    SummaryService(
      portfolioDailySummaryRepository = repository,
      transactionService = transactionService,
      cacheManager = mockk<CacheManager>(),
      clock = clock,
      summaryBatchProcessor = batchProcessor,
      summaryDeletionService = mockk<SummaryDeletionService>(),
      summaryCacheService = summaryCacheService,
      dailySummaryCalculator = mockk<DailySummaryCalculator>(),
    )

  private fun transaction(date: LocalDate): PortfolioTransaction =
    PortfolioTransaction(
      instrument = Instrument("TÖÖ:TLN", "Tööstus", "ETF", "EUR"),
      transactionType = TransactionType.BUY,
      quantity = BigDecimal.ONE,
      price = BigDecimal.TEN,
      transactionDate = date,
      platform = Platform.LHV,
    )

  private fun summary(date: LocalDate): PortfolioDailySummary =
    PortfolioDailySummary(
      entryDate = date,
      totalValue = BigDecimal.TEN,
      xirrAnnualReturn = BigDecimal.ZERO,
      realizedProfit = BigDecimal.ZERO,
      unrealizedProfit = BigDecimal.ZERO,
      totalProfit = BigDecimal.ZERO,
      earningsPerDay = BigDecimal.ZERO,
    )

  @Test
  fun `should request one week of dates from the batch processor`() {
    every { transactionService.getAllTransactions(listOf("LHV")) } returns listOf(transaction(LocalDate.of(2020, 1, 2)))
    val dates = slot<List<LocalDate>>()
    every { batchProcessor.calculateSummaries(capture(dates), any()) } returns emptyList()

    service.getSeriesForPlatforms(listOf(Platform.LHV), TimeRange.ONE_WEEK)

    expect(dates.captured).toHaveSize(7)
  }

  @Test
  fun `should cap the requested dates at the sampling limit for the max range`() {
    every { transactionService.getAllTransactions(listOf("LHV")) } returns listOf(transaction(LocalDate.of(2020, 1, 2)))
    val dates = slot<List<LocalDate>>()
    every { batchProcessor.calculateSummaries(capture(dates), any()) } returns emptyList()

    service.getSeriesForPlatforms(listOf(Platform.LHV), TimeRange.MAX)

    expect(dates.captured).toHaveSize(TimeRange.MAX_POINTS)
  }

  @Test
  fun `should return no filtered series when there are no transactions`() {
    every { transactionService.getAllTransactions(listOf("LHV")) } returns emptyList()

    expect(service.getSeriesForPlatforms(listOf(Platform.LHV), TimeRange.MAX)).toHaveSize(0)
  }

  @Test
  fun `should read stored rows between the range start and yesterday`() {
    every { repository.findAllByEntryDateBetween(any(), any()) } returns emptyList()

    service.getSeries(TimeRange.ONE_WEEK)

    verify { repository.findAllByEntryDateBetween(LocalDate.of(2026, 8, 7), LocalDate.of(2026, 8, 13)) }
  }

  @Test
  fun `should read stored rows from the epoch for the max range`() {
    every { repository.findAllByEntryDateBetween(any(), any()) } returns emptyList()

    service.getSeries(TimeRange.MAX)

    verify { repository.findAllByEntryDateBetween(LocalDate.EPOCH, LocalDate.of(2026, 8, 13)) }
  }

  @Test
  fun `should sample the stored rows down to the limit`() {
    val stored = (0 until 900).map { summary(LocalDate.of(2020, 1, 2).plusDays(it.toLong())) }
    every { repository.findAllByEntryDateBetween(any(), any()) } returns stored

    expect(service.getSeries(TimeRange.MAX)).toHaveSize(TimeRange.MAX_POINTS)
  }

  @Test
  fun `should return stored rows sorted by entry date ascending`() {
    val stored = listOf(summary(LocalDate.of(2026, 8, 12)), summary(LocalDate.of(2026, 8, 10)))
    every { repository.findAllByEntryDateBetween(any(), any()) } returns stored

    expect(service.getSeries(TimeRange.ONE_WEEK).map { it.entryDate })
      .toEqual(listOf(LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 12)))
  }
}
