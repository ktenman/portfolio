package ee.tenman.portfolio.service.summary

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioDailySummary
import ee.tenman.portfolio.service.transaction.TransactionService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.math.BigDecimal
import java.time.LocalDate

class PortfolioSummaryHistoricalServiceTest {
  private val summaryCacheService = mockk<SummaryCacheService>()
  private val platformSummaryCacheService = mockk<PlatformSummaryCacheService>()
  private val transactionService = mockk<TransactionService>()
  private val service =
    PortfolioSummaryHistoricalService(
      summaryCacheService = summaryCacheService,
      platformSummaryCacheService = platformSummaryCacheService,
      transactionService = transactionService,
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

  private fun page(date: LocalDate) = PageImpl(listOf(summary(date)), PageRequest.of(0, 30), 1)

  @Test
  fun `should read the stored page when platforms is null`() {
    val date = LocalDate.of(2023, 7, 20)
    every { summaryCacheService.getAllDailySummaries(0, 30) } returns page(date)
    every { summaryCacheService.findByEntryDate(date.minusDays(1)) } returns null

    val result = service.getHistorical(0, 30, null)

    verify(exactly = 0) { platformSummaryCacheService.getHistoricalSummariesForPlatforms(any(), any(), any()) }
    expect(result.content).toEqual(listOf(summary(date).toSummaryDto()))
  }

  @Test
  fun `should recompute the page when the selection omits a platform holding transactions`() {
    val platforms = listOf(Platform.LHV)
    val date = LocalDate.of(2023, 7, 20)
    every { transactionService.coversEveryPlatform(platforms) } returns false
    every { platformSummaryCacheService.getHistoricalSummariesForPlatforms(platforms, 0, 30) } returns page(date)
    every {
      platformSummaryCacheService.getSummaryForPlatformsOnDate(platforms, date.minusDays(1))
    } returns summary(date.minusDays(1))

    val result = service.getHistorical(0, 30, platforms)

    verify(exactly = 0) { summaryCacheService.getAllDailySummaries(any(), any()) }
    expect(result.content).toEqual(listOf(summary(date).toSummaryDto(BigDecimal.ZERO)))
  }

  @Test
  fun `should read the stored page when the selection covers every platform holding transactions`() {
    val platforms = listOf(Platform.SWEDBANK, Platform.LHV)
    val date = LocalDate.of(2023, 7, 20)
    every { transactionService.coversEveryPlatform(platforms) } returns true
    every { summaryCacheService.getAllDailySummaries(0, 30) } returns page(date)
    every { summaryCacheService.findByEntryDate(date.minusDays(1)) } returns null

    val result = service.getHistorical(0, 30, platforms)

    verify(exactly = 0) { platformSummaryCacheService.getHistoricalSummariesForPlatforms(any(), any(), any()) }
    expect(result.content).toEqual(listOf(summary(date).toSummaryDto()))
  }

  @Test
  fun `should recompute the page when the selection covers every platform but no summaries are stored`() {
    val platforms = listOf(Platform.LHV)
    val date = LocalDate.of(2023, 7, 20)
    every { transactionService.coversEveryPlatform(platforms) } returns true
    every { summaryCacheService.getAllDailySummaries(0, 30) } returns PageImpl(emptyList(), PageRequest.of(0, 30), 0)
    every { platformSummaryCacheService.getHistoricalSummariesForPlatforms(platforms, 0, 30) } returns page(date)
    every {
      platformSummaryCacheService.getSummaryForPlatformsOnDate(platforms, date.minusDays(1))
    } returns summary(date.minusDays(1))

    val result = service.getHistorical(0, 30, platforms)

    expect(result.content).toEqual(listOf(summary(date).toSummaryDto(BigDecimal.ZERO)))
  }

  @Test
  fun `should return an empty page when nothing is stored and no platforms are selected`() {
    every { summaryCacheService.getAllDailySummaries(3, 30) } returns PageImpl(emptyList(), PageRequest.of(3, 30), 0)

    val result = service.getHistorical(3, 30, null)

    expect(result.isEmpty).toEqual(true)
  }
}
