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
import io.github.resilience4j.retry.RetryConfig
import io.github.resilience4j.retry.RetryRegistry
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.boot.context.event.ApplicationReadyEvent
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
  fun `should count a skipped run as a call the breaker refused`() {
    val breakers = openAfterOneFailure()
    val service = service(mutableListOf(), breakers)
    val job = job("BinanceDataRetrievalJob") { throw PriceRefreshException("Fetched 0 of 4 prices") }

    runCatching { service.executeJob(job) }
    service.executeJob(job)

    expect(breakers.circuitBreaker("job-execution:BinanceDataRetrievalJob").metrics.numberOfNotPermittedCalls).toEqual(1L)
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

  @Test
  fun `should count a run retried three times as one breaker call`() {
    val breakers = CircuitBreakerRegistry.ofDefaults()
    val retries =
      RetryRegistry.of(
        RetryConfig
      .custom<Any>()
      .maxAttempts(3)
      .waitDuration(Duration.ofMillis(1))
      .build(),
          )
    val service = service(mutableListOf(), breakers, retries)
    var attempts = 0
    val job =
      job("Trading212DataRetrievalJob") {
        attempts++
        throw RuntimeException("Ühendus katkes")
      }

    runCatching { service.executeJob(job) }

    expect(attempts to breakers.circuitBreaker("job-execution:Trading212DataRetrievalJob").metrics.numberOfFailedCalls).toEqual(3 to 1)
  }

  @Test
  fun `should create the breaker of every job when the application is ready`() {
    val breakers = CircuitBreakerRegistry.ofDefaults()
    val event = mockk<ApplicationReadyEvent>()
    every { event.applicationContext.getBeansOfType(Job::class.java) } returns
      mapOf("poff" to job("PoffImportJob") {}, "radio" to job("RadioEpisodeJob") {})

    service(mutableListOf(), breakers).register(event)

    expect(breakers.allCircuitBreakers.map { it.name }.sorted())
      .toContainExactly("job-execution:PoffImportJob", "job-execution:RadioEpisodeJob")
  }

  private fun job(
    name: String,
    operation: () -> Unit,
  ): Job =
    object : Job {
      override fun getName(): String = name

      override fun execute() = operation()
    }

  private fun service(
    saved: MutableList<JobExecution>,
    breakers: CircuitBreakerRegistry = openAfterOneFailure(),
    retries: RetryRegistry = Resilience4jConfiguration().retryRegistry(),
  ): JobExecutionService {
    val repository = mockk<JobExecutionRepository>()
    every { repository.save(any()) } answers {
      firstArg<JobExecution>().also { saved.add(it) }
    }
    return JobExecutionService(
      JobTransactionService(repository),
      Clock.fixed(Instant.parse("2026-09-25T13:30:00Z"), ZoneOffset.UTC),
      breakers,
      retries,
    )
  }

  private fun openAfterOneFailure(): CircuitBreakerRegistry =
    CircuitBreakerRegistry.of(
      CircuitBreakerConfig
        .custom()
        .minimumNumberOfCalls(1)
        .slidingWindowSize(1)
        .waitDurationInOpenState(Duration.ofMinutes(5))
        .build(),
    )
}
