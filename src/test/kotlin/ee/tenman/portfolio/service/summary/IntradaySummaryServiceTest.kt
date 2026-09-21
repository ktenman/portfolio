package ee.tenman.portfolio.service.summary

import ch.tutteli.atrium.api.fluent.en_GB.toBeEmpty
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioDailySummary
import ee.tenman.portfolio.domain.PortfolioIntradaySummary
import ee.tenman.portfolio.domain.TimeRange
import ee.tenman.portfolio.repository.PortfolioIntradaySummaryRepository
import ee.tenman.portfolio.service.transaction.TransactionService
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class IntradaySummaryServiceTest {
  private val portfolioIntradaySummaryRepository = mockk<PortfolioIntradaySummaryRepository>()
  private val transactionService = mockk<TransactionService>()
  private val clock = Clock.fixed(Instant.parse("2026-09-20T12:34:56Z"), ZoneId.of("UTC"))
  private val replay = mockk<IntradaySummaryReplayService>()
  private val service = IntradaySummaryService(portfolioIntradaySummaryRepository, transactionService, clock, replay)

  @Test
  fun `should truncate the capture timestamp to the minute when recording a summary`() {
    val capturedAt = slot<Instant>()
    every {
      portfolioIntradaySummaryRepository.upsert(capture(capturedAt), any(), any(), any(), any(), any())
    } just runs

    service.record(summary())

    expect(capturedAt.captured).toEqual(Instant.parse("2026-09-20T12:34:00Z"))
  }

  @Test
  fun `should record the daily earnings rate rather than the monthly rate`() {
    val earningsPerDay = slot<BigDecimal>()
    every {
      portfolioIntradaySummaryRepository.upsert(any(), any(), any(), any(), any(), capture(earningsPerDay))
    } just runs

    service.record(summary())

    expect(earningsPerDay.captured).toEqualNumerically(BigDecimal("3.25"))
  }

  @Test
  fun `should record the whole portfolio key when no platform is given`() {
    every { portfolioIntradaySummaryRepository.upsert(any(), any(), any(), any(), any(), any()) } just runs

    service.record(summary())

    verify { portfolioIntradaySummaryRepository.upsert(any(), "", any(), any(), any(), any()) }
  }

  @Test
  fun `should record the platform key of the platform the summary belongs to`() {
    every { portfolioIntradaySummaryRepository.upsert(any(), any(), any(), any(), any(), any()) } just runs

    service.record(summary(), Platform.LIGHTYEAR_BUSINESS)

    verify { portfolioIntradaySummaryRepository.upsert(any(), "LIGHTYEAR_BUSINESS", any(), any(), any(), any()) }
  }

  @Test
  fun `should bucket a one day range into two hundred and eighty eight second windows`() {
    val bucketSeconds = captureBucketSeconds()

    service.getPoints(TimeRange.ONE_DAY, null)

    expect(bucketSeconds.captured).toEqual(288L)
  }

  @Test
  fun `should bucket a six day range into one thousand seven hundred and twenty eight second windows`() {
    val bucketSeconds = captureBucketSeconds()

    service.getPoints(TimeRange.SIX_DAYS, null)

    expect(bucketSeconds.captured).toEqual(1728L)
  }

  @Test
  fun `should bucket a one week range into two thousand and sixteen second windows`() {
    val bucketSeconds = captureBucketSeconds()

    service.getPoints(TimeRange.ONE_WEEK, null)

    expect(bucketSeconds.captured).toEqual(2016L)
  }

  @Test
  fun `should read from the start of the requested range`() {
    val from = slot<Instant>()
    every { portfolioIntradaySummaryRepository.findBucketed(capture(from), any(), any()) } returns emptyList()

    service.getPoints(TimeRange.TWO_DAYS, null)

    expect(from.captured).toEqual(Instant.parse("2026-09-18T12:34:56Z"))
  }

  @Test
  fun `should expose the monthly earnings rate for a captured point`() {
    every { portfolioIntradaySummaryRepository.findBucketed(any(), any(), any()) } returns listOf(point())

    val points = service.getPoints(TimeRange.ONE_DAY, null)

    expect(points.single().earningsPerMonth).toEqualNumerically(BigDecimal("98.921875"))
  }

  @Test
  fun `should return no points when the range is wider than one week`() {
    expect(service.getPoints(TimeRange.ONE_MONTH, null)).toBeEmpty()
  }

  @Test
  fun `should return points when the range is one week`() {
    every { portfolioIntradaySummaryRepository.findBucketed(any(), any(), any()) } returns listOf(point())

    expect(service.getPoints(TimeRange.ONE_WEEK, null)).toHaveSize(1)
  }

  @Test
  fun `should return no points when the range has no fixed start`() {
    expect(service.getPoints(TimeRange.MAX, null)).toBeEmpty()
  }

  @Test
  fun `should read the whole portfolio key when no platform filter is given`() {
    every { portfolioIntradaySummaryRepository.findBucketed(any(), any(), any()) } returns emptyList()

    service.getPoints(TimeRange.ONE_DAY, null)

    verify { portfolioIntradaySummaryRepository.findBucketed(any(), any(), "") }
  }

  @Test
  fun `should read the whole portfolio key when the platform filter covers every platform`() {
    every { transactionService.coversEveryPlatform(listOf(Platform.LIGHTYEAR)) } returns true
    every { portfolioIntradaySummaryRepository.findBucketed(any(), any(), any()) } returns emptyList()

    service.getPoints(TimeRange.ONE_DAY, listOf(Platform.LIGHTYEAR))

    verify { portfolioIntradaySummaryRepository.findBucketed(any(), any(), "") }
  }

  @Test
  fun `should read the platform key when a single platform is selected`() {
    every { transactionService.coversEveryPlatform(listOf(Platform.LIGHTYEAR)) } returns false
    every { portfolioIntradaySummaryRepository.findBucketed(any(), any(), any()) } returns emptyList()

    service.getPoints(TimeRange.ONE_DAY, listOf(Platform.LIGHTYEAR))

    verify { portfolioIntradaySummaryRepository.findBucketed(any(), any(), "LIGHTYEAR") }
  }

  @Test
  fun `should return points when a partial selection of several platforms is requested`() {
    val selection = listOf(Platform.LHV, Platform.LIGHTYEAR)
    every { transactionService.coversEveryPlatform(selection) } returns false
    every { replay.getPoints(TimeRange.ONE_DAY, selection) } returns listOf(point().toIntradayPointDto(), point().toIntradayPointDto())
    expect(service.getPoints(TimeRange.ONE_DAY, selection)).toHaveSize(2)
  }

  @Test
  fun `should still read a stored single platform when the selection contains duplicates`() {
    every { transactionService.coversEveryPlatform(listOf(Platform.LIGHTYEAR)) } returns false
    every { portfolioIntradaySummaryRepository.findBucketed(any(), any(), "LIGHTYEAR") } returns listOf(point())
    expect(service.getPoints(TimeRange.ONE_DAY, listOf(Platform.LIGHTYEAR, Platform.LIGHTYEAR))).toHaveSize(1)
  }

  @Test
  fun `should normalize a partial selection before replaying it`() {
    val normalized = listOf(Platform.LHV, Platform.LIGHTYEAR)
    every { transactionService.coversEveryPlatform(normalized) } returns false
    every { replay.getPoints(TimeRange.ONE_DAY, normalized) } returns listOf(point().toIntradayPointDto())
    expect(service.getPoints(TimeRange.ONE_DAY, listOf(Platform.LIGHTYEAR, Platform.LHV, Platform.LIGHTYEAR))).toHaveSize(1)
  }

  @Test
  fun `should delete the captured points older than the given cutoff`() {
    val cutoff = Instant.parse("2026-08-21T04:30:00Z")
    every { portfolioIntradaySummaryRepository.deleteOlderThan(cutoff) } just runs

    service.deleteOlderThan(cutoff)

    verify { portfolioIntradaySummaryRepository.deleteOlderThan(cutoff) }
  }

  private fun captureBucketSeconds(): CapturingSlot<Long> {
    val bucketSeconds = slot<Long>()
    every { portfolioIntradaySummaryRepository.findBucketed(any(), capture(bucketSeconds), any()) } returns emptyList()
    return bucketSeconds
  }

  private fun point() =
    PortfolioIntradaySummary(
      capturedAt = Instant.parse("2026-09-20T12:00:00Z"),
      platformKey = "",
      totalValue = BigDecimal("1000.00"),
      xirrAnnualReturn = BigDecimal("0.1857"),
      totalProfit = BigDecimal("250.00"),
      earningsPerDay = BigDecimal("3.25"),
    )

  private fun summary() =
    PortfolioDailySummary(
      entryDate = LocalDate.of(2026, 9, 20),
      totalValue = BigDecimal("1000.00"),
      xirrAnnualReturn = BigDecimal("0.1857"),
      totalProfit = BigDecimal("250.00"),
      earningsPerDay = BigDecimal("3.25"),
    )
}
