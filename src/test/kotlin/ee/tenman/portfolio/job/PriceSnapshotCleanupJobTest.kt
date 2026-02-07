package ee.tenman.portfolio.job

import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import ee.tenman.portfolio.service.pricing.PriceSnapshotService
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class PriceSnapshotCleanupJobTest {
  private val jobExecutionService = mockk<JobExecutionService>()
  private val priceSnapshotService = mockk<PriceSnapshotService>()
  private val fixedInstant = Instant.parse("2024-02-15T03:00:00Z")
  private val clock = Clock.fixed(fixedInstant, ZoneId.of("UTC"))
  private val job = PriceSnapshotCleanupJob(jobExecutionService, priceSnapshotService, clock)

  @BeforeEach
  fun setUp() {
    clearMocks(jobExecutionService, priceSnapshotService)
  }

  @Test
  fun `should delete snapshots older than 30 days`() {
    val expectedCutoff = fixedInstant.minus(30, ChronoUnit.DAYS)
    every { priceSnapshotService.deleteOlderThan(expectedCutoff) } just runs

    job.execute()

    verify { priceSnapshotService.deleteOlderThan(expectedCutoff) }
  }
}
