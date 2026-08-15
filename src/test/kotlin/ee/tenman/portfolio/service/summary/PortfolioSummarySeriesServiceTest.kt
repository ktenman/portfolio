package ee.tenman.portfolio.service.summary

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioDailySummary
import ee.tenman.portfolio.domain.TimeRange
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class PortfolioSummarySeriesServiceTest {
  private val summaryService = mockk<SummaryService>()
  private val platformSummaryCacheService = mockk<PlatformSummaryCacheService>()
  private val service =
    PortfolioSummarySeriesService(
      summaryService = summaryService,
      platformSummaryCacheService = platformSummaryCacheService,
    )

  private fun summary(): PortfolioDailySummary =
    PortfolioDailySummary(
      entryDate = LocalDate.of(2023, 7, 20),
      totalValue = BigDecimal.TEN,
      xirrAnnualReturn = BigDecimal.ZERO,
      realizedProfit = BigDecimal.ZERO,
      unrealizedProfit = BigDecimal.ZERO,
      totalProfit = BigDecimal.ZERO,
      earningsPerDay = BigDecimal.ZERO,
    )

  @Test
  fun `should fetch the unfiltered series from summary service when platforms is null`() {
    every { summaryService.getSeries(TimeRange.SIX_MONTHS) } returns listOf(summary())

    val result = service.getSeries(TimeRange.SIX_MONTHS, null)

    verify(exactly = 0) { platformSummaryCacheService.getSeriesForPlatforms(any(), any()) }
    expect(result).toEqual(listOf(summary().toSummaryDto()))
  }

  @Test
  fun `should fetch the platform filtered series when platforms is provided`() {
    val platforms = listOf(Platform.LHV)
    every { platformSummaryCacheService.getSeriesForPlatforms(platforms, TimeRange.SIX_MONTHS) } returns listOf(summary())

    val result = service.getSeries(TimeRange.SIX_MONTHS, platforms)

    verify(exactly = 0) { summaryService.getSeries(any()) }
    expect(result).toEqual(listOf(summary().toSummaryDto()))
  }
}
