package ee.tenman.portfolio.service.summary

import ch.tutteli.atrium.api.fluent.en_GB.toBeEmpty
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioDailySummary
import ee.tenman.portfolio.domain.PortfolioIntradaySummary
import ee.tenman.portfolio.domain.TimeRange
import ee.tenman.portfolio.repository.PortfolioIntradaySummaryRepository
import ee.tenman.portfolio.service.transaction.TransactionService
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
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
  private val service = IntradaySummaryService(portfolioIntradaySummaryRepository, transactionService, clock)

  @Test
  fun `should truncate the capture timestamp to the minute when recording a summary`() {
    val capturedAt = slot<Instant>()
    every {
      portfolioIntradaySummaryRepository.upsert(capture(capturedAt), any(), any(), any(), any())
    } just runs

    service.record(summary())

    expect(capturedAt.captured).toEqual(Instant.parse("2026-09-20T12:34:00Z"))
  }

  @Test
  fun `should record the daily earnings rate rather than the monthly rate`() {
    val earningsPerDay = slot<BigDecimal>()
    every {
      portfolioIntradaySummaryRepository.upsert(any(), any(), any(), any(), capture(earningsPerDay))
    } just runs

    service.record(summary())

    expect(earningsPerDay.captured).toEqualNumerically(BigDecimal("3.25"))
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
  fun `should read from the start of the requested range`() {
    val from = slot<Instant>()
    every { portfolioIntradaySummaryRepository.findBucketed(capture(from), any()) } returns emptyList()

    service.getPoints(TimeRange.TWO_DAYS, null)

    expect(from.captured).toEqual(Instant.parse("2026-09-18T12:34:56Z"))
  }

  @Test
  fun `should expose the monthly earnings rate for a captured point`() {
    every { portfolioIntradaySummaryRepository.findBucketed(any(), any()) } returns listOf(point())

    val points = service.getPoints(TimeRange.ONE_DAY, null)

    expect(points.single().earningsPerMonth).toEqualNumerically(BigDecimal("98.921875"))
  }

  @Test
  fun `should return no points when the range is wider than a week`() {
    expect(service.getPoints(TimeRange.ONE_MONTH, null)).toBeEmpty()
  }

  @Test
  fun `should return no points when the range has no fixed start`() {
    expect(service.getPoints(TimeRange.MAX, null)).toBeEmpty()
  }

  @Test
  fun `should return no points when the platform filter dont cover every platform`() {
    every { transactionService.coversEveryPlatform(listOf(Platform.LIGHTYEAR)) } returns false

    expect(service.getPoints(TimeRange.ONE_DAY, listOf(Platform.LIGHTYEAR))).toBeEmpty()
  }

  @Test
  fun `should return points when the platform filter covers every platform`() {
    every { transactionService.coversEveryPlatform(listOf(Platform.LIGHTYEAR)) } returns true
    every { portfolioIntradaySummaryRepository.findBucketed(any(), any()) } returns listOf(point())

    expect(service.getPoints(TimeRange.ONE_DAY, listOf(Platform.LIGHTYEAR))).toEqual(
      service.getPoints(TimeRange.ONE_DAY, null),
    )
  }

  @Test
  fun `should delete the captured points older than the given cutoff`() {
    val cutoff = Instant.parse("2026-08-21T04:30:00Z")
    every { portfolioIntradaySummaryRepository.deleteOlderThan(cutoff) } returns 7

    service.deleteOlderThan(cutoff)

    expect(portfolioIntradaySummaryRepository.deleteOlderThan(cutoff)).toEqual(7)
  }

  private fun captureBucketSeconds(): io.mockk.CapturingSlot<Long> {
    val bucketSeconds = slot<Long>()
    every { portfolioIntradaySummaryRepository.findBucketed(any(), capture(bucketSeconds)) } returns emptyList()
    return bucketSeconds
  }

  private fun point() =
    PortfolioIntradaySummary(
      capturedAt = Instant.parse("2026-09-20T12:00:00Z"),
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
