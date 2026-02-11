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

class PriceSnapshotCleanupJobTest {
  private val jobExecutionService = mockk<JobExecutionService>()
  private val priceSnapshotService = mockk<PriceSnapshotService>()
  private val clock = Clock.fixed(Instant.parse("2024-02-15T03:00:00Z"), ZoneId.of("UTC"))

  @BeforeEach
  fun setUp() {
    clearMocks(jobExecutionService, priceSnapshotService)
  }

  @Test
  fun `should delete snapshots older than 30 days by default`() {
    val job = PriceSnapshotCleanupJob(jobExecutionService, priceSnapshotService, clock, 30)
    val expectedCutoff = Instant.parse("2024-01-16T03:00:00Z")
    every { priceSnapshotService.deleteOlderThan(expectedCutoff) } just runs

    job.execute()

    verify { priceSnapshotService.deleteOlderThan(expectedCutoff) }
  }

  @Test
  fun `should use configurable retention days`() {
    val job = PriceSnapshotCleanupJob(jobExecutionService, priceSnapshotService, clock, 7)
    val expectedCutoff = Instant.parse("2024-02-08T03:00:00Z")
    every { priceSnapshotService.deleteOlderThan(expectedCutoff) } just runs

    job.execute()

    verify { priceSnapshotService.deleteOlderThan(expectedCutoff) }
  }
}
