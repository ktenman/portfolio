package ee.tenman.portfolio.job

import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import ee.tenman.portfolio.service.radio.RadioDigestService
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class RadioDigestJobTest {
  private val clock = Clock.fixed(Instant.parse("2026-11-16T22:30:00Z"), ZoneId.of("Europe/Tallinn"))

  @Test
  fun `should build the digest for today in Tallinn`() {
    val service = mockk<RadioDigestService> { every { build(any()) } just runs }
    RadioDigestJob(service, mockk<JobExecutionService>(), clock).execute()
    verify { service.build(LocalDate.of(2026, 11, 17)) }
  }

  @Test
  fun `should not record a run when today's digest is built`() {
    val service = mockk<RadioDigestService> { every { due(any()) } returns false }
    val executions = mockk<JobExecutionService>(relaxed = true)
    RadioDigestJob(service, executions, clock).runScheduledJob()
    verify(exactly = 0) { executions.executeJob(any()) }
  }

  @Test
  fun `should record a run while today's digest is missing`() {
    val service = mockk<RadioDigestService> { every { due(LocalDate.of(2026, 11, 17)) } returns true }
    val executions = mockk<JobExecutionService>(relaxed = true)
    val job = RadioDigestJob(service, executions, clock)
    job.runScheduledJob()
    verify { executions.executeJob(job) }
  }
}
