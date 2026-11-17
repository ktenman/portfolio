package ee.tenman.portfolio.job

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.LiveUpdate
import ee.tenman.portfolio.model.CollectionSchedules
import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import ee.tenman.portfolio.service.infrastructure.LiveUpdateService
import ee.tenman.portfolio.service.radio.RadioDigestService
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.Test
import org.springframework.scheduling.support.CronExpression
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class RadioDigestJobTest {
  private val clock = Clock.fixed(Instant.parse("2026-11-16T22:30:00Z"), ZoneId.of("Europe/Tallinn"))

  @Test
  fun `should build the digest for today in Tallinn`() {
    val service = mockk<RadioDigestService> { every { build(any()) } just runs }
    RadioDigestJob(service, mockk<JobExecutionService>(), mockk<LiveUpdateService>(), clock).execute()
    verify { service.build(LocalDate.of(2026, 11, 17)) }
  }

  @Test
  fun `should not record a run when today's digest is built`() {
    val service = mockk<RadioDigestService> { every { due(any()) } returns false }
    val executions = mockk<JobExecutionService>(relaxed = true)
    RadioDigestJob(service, executions, mockk<LiveUpdateService>(relaxed = true), clock).runScheduledJob()
    verify(exactly = 0) { executions.executeJob(any()) }
  }

  @Test
  fun `should record a run while today's digest is missing`() {
    val service = mockk<RadioDigestService> { every { due(LocalDate.of(2026, 11, 17)) } returns true }
    val executions = mockk<JobExecutionService>(relaxed = true)
    val job = RadioDigestJob(service, executions, mockk<LiveUpdateService>(relaxed = true), clock)
    job.runScheduledJob()
    verify { executions.executeJob(job) }
  }

  @Test
  fun `should try the digest every hour from noon to the end of the day`() {
    val cron = CronExpression.parse(CollectionSchedules.RADIO_DIGEST_CRON)
    val day = LocalDate.of(2026, 11, 17)
    val hours = generateSequence(cron.next(day.atStartOfDay().minusSeconds(1))) { cron.next(it) }.takeWhile { it.toLocalDate() == day }
    expect(hours.map { it.hour }.toList()).toEqual((12..23).toList())
  }

  @Test
  fun `should refresh the digest once a failed run is recorded`() {
    val service = mockk<RadioDigestService> { every { due(any()) } returns true }
    val executions = mockk<JobExecutionService> { every { executeJob(any()) } throws IllegalStateException("Mudel ei vastanud") }
    val updates = mockk<LiveUpdateService>(relaxed = true)
    val job = RadioDigestJob(service, executions, updates, clock)
    runCatching { job.runScheduledJob() }
    verifyOrder {
      executions.executeJob(job)
      updates.publish(LiveUpdate.DIGEST)
    }
  }

  @Test
  fun `should leave the refresh to the build after a run that succeeded`() {
    val service = mockk<RadioDigestService> { every { due(any()) } returns true }
    val updates = mockk<LiveUpdateService>(relaxed = true)
    RadioDigestJob(service, mockk<JobExecutionService>(relaxed = true), updates, clock).runScheduledJob()
    verify(exactly = 0) { updates.publish(any()) }
  }
}
