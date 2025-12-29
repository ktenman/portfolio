package ee.tenman.portfolio.job

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.service.infrastructure.JobTransactionService
import ee.tenman.portfolio.service.integration.WisdomTreeUpdateService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class WisdomTreeDataUpdateJobTest {
  private val jobTransactionService: JobTransactionService = mockk(relaxed = true)
  private val wisdomTreeUpdateService: WisdomTreeUpdateService = mockk()
  private val clock = Clock.fixed(Instant.parse("2024-01-15T10:00:00Z"), ZoneId.of("UTC"))

  private val job = WisdomTreeDataUpdateJob(jobTransactionService, wisdomTreeUpdateService, clock)

  @Test
  fun `should execute WisdomTree update successfully`() {
    val expectedResult = mapOf("deleted" to 50, "created" to 125)
    every { wisdomTreeUpdateService.updateWtaiHoldings() } returns expectedResult

    job.execute()

    verify(exactly = 1) { wisdomTreeUpdateService.updateWtaiHoldings() }
  }

  @Test
  fun `should have correct job name`() {
    expect(job.getName()).toEqual("WisdomTreeDataUpdateJob")
  }
}
