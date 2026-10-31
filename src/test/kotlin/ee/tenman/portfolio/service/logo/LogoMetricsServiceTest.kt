package ee.tenman.portfolio.service.logo

import ch.tutteli.atrium.api.fluent.en_GB.toContainExactly
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.JobExecution
import ee.tenman.portfolio.domain.JobStatus
import ee.tenman.portfolio.repository.JobExecutionRepository
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.time.Instant

class LogoMetricsServiceTest {
  private val jobExecutionRepository = mockk<JobExecutionRepository>()

  private fun gauge(vararg statuses: JobStatus?): Gauge {
    every { jobExecutionRepository.findFirstByJobNameOrderByStartTimeDesc("EtfLogoCollectionJob") } returnsMany
      statuses.map { status -> status?.let { JobExecution("EtfLogoCollectionJob", Instant.EPOCH, Instant.EPOCH, 0, it) } }
    val registry = SimpleMeterRegistry()
    LogoMetricsService(jobExecutionRepository).bindTo(registry)
    return registry.get("portfolio.logo.collection.failed").gauge()
  }

  @Test
  fun `should show 1 when the latest logo run failed`() {
    expect(gauge(JobStatus.FAILURE).value()).toEqual(1.0)
  }

  @Test
  fun `should show 0 when the latest logo run succeeded`() {
    expect(gauge(JobStatus.SUCCESS).value()).toEqual(0.0)
  }

  @Test
  fun `should show 0 before the first logo run`() {
    expect(gauge(null).value()).toEqual(0.0)
  }

  @Test
  fun `should read the latest run on every scrape`() {
    val gauge = gauge(JobStatus.FAILURE, JobStatus.SUCCESS)
    expect(listOf(gauge.value(), gauge.value())).toContainExactly(1.0, 0.0)
  }
}
