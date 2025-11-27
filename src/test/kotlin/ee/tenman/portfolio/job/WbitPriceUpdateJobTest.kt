package ee.tenman.portfolio.job

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.service.JobExecutionService
import ee.tenman.portfolio.service.WbitPriceUpdateService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class WbitPriceUpdateJobTest {
  private val jobExecutionService = mockk<JobExecutionService>(relaxed = true)
  private val wbitPriceUpdateService = mockk<WbitPriceUpdateService>()

  @Test
  fun `should execute within operating hours at 23 30`() {
    val clock =
      Clock.fixed(
      Instant.parse("2025-11-27T21:30:00Z"),
      ZoneId.of("Europe/Tallinn"),
    )
    val job = WbitPriceUpdateJob(jobExecutionService, wbitPriceUpdateService, clock)

    expect(job.isWithinOperatingHours()).toEqual(true)
  }

  @Test
  fun `should execute within operating hours at 03 00`() {
    val clock =
      Clock.fixed(
      Instant.parse("2025-11-27T01:00:00Z"),
      ZoneId.of("Europe/Tallinn"),
    )
    val job = WbitPriceUpdateJob(jobExecutionService, wbitPriceUpdateService, clock)

    expect(job.isWithinOperatingHours()).toEqual(true)
  }

  @Test
  fun `should execute within operating hours at 08 00`() {
    val clock =
      Clock.fixed(
      Instant.parse("2025-11-27T06:00:00Z"),
      ZoneId.of("Europe/Tallinn"),
    )
    val job = WbitPriceUpdateJob(jobExecutionService, wbitPriceUpdateService, clock)

    expect(job.isWithinOperatingHours()).toEqual(true)
  }

  @Test
  fun `should execute within operating hours at exactly 23 00`() {
    val clock =
      Clock.fixed(
      Instant.parse("2025-11-27T21:00:00Z"),
      ZoneId.of("Europe/Tallinn"),
    )
    val job = WbitPriceUpdateJob(jobExecutionService, wbitPriceUpdateService, clock)

    expect(job.isWithinOperatingHours()).toEqual(true)
  }

  @Test
  fun `should execute within operating hours at exactly 08 30`() {
    val clock =
      Clock.fixed(
      Instant.parse("2025-11-27T06:30:00Z"),
      ZoneId.of("Europe/Tallinn"),
    )
    val job = WbitPriceUpdateJob(jobExecutionService, wbitPriceUpdateService, clock)

    expect(job.isWithinOperatingHours()).toEqual(true)
  }

  @Test
  fun `should skip outside operating hours at 12 00`() {
    val clock =
      Clock.fixed(
      Instant.parse("2025-11-27T10:00:00Z"),
      ZoneId.of("Europe/Tallinn"),
    )
    val job = WbitPriceUpdateJob(jobExecutionService, wbitPriceUpdateService, clock)

    expect(job.isWithinOperatingHours()).toEqual(false)
  }

  @Test
  fun `should skip outside operating hours at 10 00`() {
    val clock =
      Clock.fixed(
      Instant.parse("2025-11-27T08:00:00Z"),
      ZoneId.of("Europe/Tallinn"),
    )
    val job = WbitPriceUpdateJob(jobExecutionService, wbitPriceUpdateService, clock)

    expect(job.isWithinOperatingHours()).toEqual(false)
  }

  @Test
  fun `should skip outside operating hours at 18 00`() {
    val clock =
      Clock.fixed(
      Instant.parse("2025-11-27T16:00:00Z"),
      ZoneId.of("Europe/Tallinn"),
    )
    val job = WbitPriceUpdateJob(jobExecutionService, wbitPriceUpdateService, clock)

    expect(job.isWithinOperatingHours()).toEqual(false)
  }

  @Test
  fun `should skip outside operating hours at 08 31`() {
    val clock =
      Clock.fixed(
      Instant.parse("2025-11-27T06:31:00Z"),
      ZoneId.of("Europe/Tallinn"),
    )
    val job = WbitPriceUpdateJob(jobExecutionService, wbitPriceUpdateService, clock)

    expect(job.isWithinOperatingHours()).toEqual(false)
  }

  @Test
  fun `should skip outside operating hours at 22 59`() {
    val clock =
      Clock.fixed(
      Instant.parse("2025-11-27T20:59:00Z"),
      ZoneId.of("Europe/Tallinn"),
    )
    val job = WbitPriceUpdateJob(jobExecutionService, wbitPriceUpdateService, clock)

    expect(job.isWithinOperatingHours()).toEqual(false)
  }

  @Test
  fun `should execute job and call service`() {
    val clock =
      Clock.fixed(
      Instant.parse("2025-11-27T01:00:00Z"),
      ZoneId.of("Europe/Tallinn"),
    )
    val job = WbitPriceUpdateJob(jobExecutionService, wbitPriceUpdateService, clock)
    every { wbitPriceUpdateService.updateWbitPrice() } returns Unit

    job.execute()

    verify(exactly = 1) { wbitPriceUpdateService.updateWbitPrice() }
  }

  @Test
  fun `should have correct job name`() {
    val clock = Clock.fixed(Instant.now(), ZoneId.of("UTC"))
    val job = WbitPriceUpdateJob(jobExecutionService, wbitPriceUpdateService, clock)

    expect(job.getName()).toEqual("WbitPriceUpdateJob")
  }
}
