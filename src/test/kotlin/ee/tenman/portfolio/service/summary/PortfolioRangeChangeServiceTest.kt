package ee.tenman.portfolio.service.summary

import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioDailySummary
import ee.tenman.portfolio.domain.TimeRange
import ee.tenman.portfolio.dto.PortfolioSummaryDto
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class PortfolioRangeChangeServiceTest {
  private val seriesService = mockk<PortfolioSummarySeriesService>()
  private val currentDaySummaryCacheService = mockk<CurrentDaySummaryCacheService>()
  private val platformSummaryCacheService = mockk<PlatformSummaryCacheService>()
  private val clock = Clock.fixed(Instant.parse("2026-08-14T12:00:00Z"), ZoneId.of("UTC"))
  private val service =
    PortfolioRangeChangeService(seriesService, currentDaySummaryCacheService, platformSummaryCacheService, clock)

  @Test
  fun `should report the whole profit when the range opens before the first recorded day`() {
    every { currentDaySummaryCacheService.getCurrentDaySummary() } returns
      summary(LocalDate.of(2026, 8, 14), BigDecimal("24532.90"), BigDecimal("818.96"))
    every { seriesService.getSeries(TimeRange.ONE_YEAR, null) } returns
      listOf(point(LocalDate.of(2026, 6, 16), BigDecimal("5897.21"), BigDecimal("-102.79")))

    expect(service.calculate(TimeRange.ONE_YEAR, null).changeAmount).toEqualNumerically(BigDecimal("818.96"))
  }

  @Test
  fun `should subtract the profit recorded on the range start date`() {
    every { currentDaySummaryCacheService.getCurrentDaySummary() } returns
      summary(LocalDate.of(2026, 8, 14), BigDecimal("24532.90"), BigDecimal("818.96"))
    every { seriesService.getSeries(TimeRange.ONE_WEEK, null) } returns
      listOf(point(LocalDate.of(2026, 8, 7), BigDecimal("24138.46"), BigDecimal("424.52")))

    expect(service.calculate(TimeRange.ONE_WEEK, null).changeAmount).toEqualNumerically(BigDecimal("394.44"))
  }

  @Test
  fun `should express the change as a percentage of the current value`() {
    every { currentDaySummaryCacheService.getCurrentDaySummary() } returns
      summary(LocalDate.of(2026, 8, 14), BigDecimal("24532.90"), BigDecimal("818.96"))
    every { seriesService.getSeries(TimeRange.ONE_YEAR, null) } returns
      listOf(point(LocalDate.of(2026, 6, 16), BigDecimal("5897.21"), BigDecimal("-102.79")))

    expect(service.calculate(TimeRange.ONE_YEAR, null).changePercent).toEqualNumerically(BigDecimal("3.3382"))
  }

  @Test
  fun `should treat the max range as opening from zero profit`() {
    every { currentDaySummaryCacheService.getCurrentDaySummary() } returns
      summary(LocalDate.of(2026, 8, 14), BigDecimal("24532.90"), BigDecimal("818.96"))

    expect(service.calculate(TimeRange.MAX, null).changeAmount).toEqualNumerically(BigDecimal("818.96"))
  }

  @Test
  fun `should report a zero percentage when the current value is not positive`() {
    every { currentDaySummaryCacheService.getCurrentDaySummary() } returns
      summary(LocalDate.of(2026, 8, 14), BigDecimal.ZERO, BigDecimal("500.00"))

    expect(service.calculate(TimeRange.MAX, null).changePercent).toEqualNumerically(BigDecimal.ZERO)
  }

  @Test
  fun `should report the whole gain as the percentage when every euro held is profit`() {
    every { currentDaySummaryCacheService.getCurrentDaySummary() } returns
      summary(LocalDate.of(2026, 8, 14), BigDecimal("500.00"), BigDecimal("500.00"))

    expect(service.calculate(TimeRange.MAX, null).changePercent).toEqualNumerically(BigDecimal("100"))
  }

  @Test
  fun `should report a zero change when the range has no recorded days`() {
    every { currentDaySummaryCacheService.getCurrentDaySummary() } returns
      summary(LocalDate.of(2026, 8, 14), BigDecimal.ZERO, BigDecimal.ZERO)
    every { seriesService.getSeries(TimeRange.SIX_MONTHS, null) } returns emptyList()

    expect(service.calculate(TimeRange.SIX_MONTHS, null).changeAmount).toEqualNumerically(BigDecimal.ZERO)
  }

  @Test
  fun `should read the current summary of the selected platforms`() {
    val platforms = listOf(Platform.LIGHTYEAR_BUSINESS)
    every { platformSummaryCacheService.getCurrentDaySummaryForPlatforms(platforms) } returns
      summary(LocalDate.of(2026, 8, 14), BigDecimal("24532.90"), BigDecimal("818.96"))
    every { seriesService.getSeries(TimeRange.FIVE_YEARS, platforms) } returns
      listOf(point(LocalDate.of(2026, 6, 16), BigDecimal("5897.21"), BigDecimal("-102.79")))

    expect(service.calculate(TimeRange.FIVE_YEARS, platforms).changeAmount).toEqualNumerically(BigDecimal("818.96"))
  }

  private fun summary(
    entryDate: LocalDate,
    totalValue: BigDecimal,
    totalProfit: BigDecimal,
  ): PortfolioDailySummary =
    PortfolioDailySummary(
      entryDate = entryDate,
      totalValue = totalValue,
      xirrAnnualReturn = BigDecimal("0.24"),
      totalProfit = totalProfit,
      earningsPerDay = BigDecimal("16.14"),
    )

  private fun point(
    date: LocalDate,
    totalValue: BigDecimal,
    totalProfit: BigDecimal,
  ): PortfolioSummaryDto =
    PortfolioSummaryDto(
      date = date,
      totalValue = totalValue,
      xirrAnnualReturn = BigDecimal("0.24"),
      realizedProfit = BigDecimal.ZERO,
      unrealizedProfit = totalProfit,
      totalProfit = totalProfit,
      earningsPerDay = BigDecimal("16.14"),
      earningsPerMonth = BigDecimal("491.33"),
    )
}
