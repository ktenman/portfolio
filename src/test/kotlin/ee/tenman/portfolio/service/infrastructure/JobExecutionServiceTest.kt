package ee.tenman.portfolio.service.infrastructure

import ch.tutteli.atrium.api.fluent.en_GB.toContainExactly
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.Resilience4jConfiguration
import ee.tenman.portfolio.domain.JobExecution
import ee.tenman.portfolio.domain.JobStatus
import ee.tenman.portfolio.exception.PriceRefreshException
import ee.tenman.portfolio.job.Job
import ee.tenman.portfolio.repository.JobExecutionRepository
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class JobExecutionServiceTest {
  @Test
  fun `should continue unrelated jobs when the Lightyear circuit opens`() {
    val saved = mutableListOf<JobExecution>()
    val service = service(saved)
    val executions = mutableListOf<String>()
    val lightyear = job("LightyearPriceRetrievalJob") { throw PriceRefreshException("Fetched 0 of 33 prices") }
    val trading = job("Trading212DataRetrievalJob") { executions.add("TRADING212") }

    runCatching { service.executeJob(lightyear) }
    service.executeJob(lightyear)
    service.executeJob(trading)

    expect(executions).toContainExactly("TRADING212")
    expect(saved.map { it.status }).toContainExactly(JobStatus.FAILURE, JobStatus.SKIPPED, JobStatus.SUCCESS)
  }

  @Test
  fun `should record a failed collection without retrying the same refresh`() {
    val saved = mutableListOf<JobExecution>()
    val service = service(saved)
    var attempts = 0
    val job =
      job("LightyearPriceRetrievalJob") {
        attempts++
        throw PriceRefreshException("Fetched 0 of 33 prices")
      }

    runCatching { service.executeJob(job) }

    expect(attempts).toEqual(1)
    expect(saved.map { it.status }).toContainExactly(JobStatus.FAILURE)
    expect(saved.single().message).toEqual("Fetched 0 of 33 prices")
  }

  private fun job(
    name: String,
    operation: () -> Unit,
  ): Job =
    object : Job {
      override fun getName(): String = name

      override fun execute() = operation()
    }

  private fun service(saved: MutableList<JobExecution>): JobExecutionService {
    val repository = mockk<JobExecutionRepository>()
    every { repository.save(any()) } answers {
      firstArg<JobExecution>().also { saved.add(it) }
    }
    val config =
      CircuitBreakerConfig
        .custom()
        .minimumNumberOfCalls(1)
        .slidingWindowSize(1)
        .waitDurationInOpenState(Duration.ofMinutes(5))
        .build()
    return JobExecutionService(
      JobTransactionService(repository),
      Clock.fixed(Instant.parse("2026-09-25T13:30:00Z"), ZoneOffset.UTC),
      CircuitBreakerRegistry.of(config),
      Resilience4jConfiguration().retryRegistry(),
    )
  }
}
