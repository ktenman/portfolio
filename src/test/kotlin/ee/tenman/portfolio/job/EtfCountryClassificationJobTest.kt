package ee.tenman.portfolio.job

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.IndustryClassificationProperties
import ee.tenman.portfolio.domain.EtfHolding
import ee.tenman.portfolio.openrouter.OpenRouterCircuitBreaker
import ee.tenman.portfolio.service.etf.EtfHoldingPersistenceService
import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import ee.tenman.portfolio.service.integration.BatchClassificationOutcome
import ee.tenman.portfolio.service.integration.CountryClassificationResult
import ee.tenman.portfolio.service.integration.CountryClassificationService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.scheduling.support.CronExpression

class EtfCountryClassificationJobTest {
  private val etfHoldingPersistenceService: EtfHoldingPersistenceService = mockk(relaxed = true)
  private val countryClassificationService: CountryClassificationService = mockk()
  private val jobExecutionService: JobExecutionService = mockk(relaxed = true)
  private val circuitBreaker: OpenRouterCircuitBreaker = mockk()
  private val properties: IndustryClassificationProperties = mockk()
  private lateinit var job: EtfCountryClassificationJob

  @BeforeEach
  fun setup() {
    every { circuitBreaker.getWaitTimeMs(any()) } returns 0L
    every { circuitBreaker.isUsingFallback() } returns false
    every { properties.rateLimitBufferMs } returns 100L
    every { properties.enabled } returns true
    job =
      EtfCountryClassificationJob(
        etfHoldingPersistenceService = etfHoldingPersistenceService,
        countryClassificationService = countryClassificationService,
        jobExecutionService = jobExecutionService,
        circuitBreaker = circuitBreaker,
        properties = properties,
      )
  }

  @Test
  fun `cannot increment attempts when model gives no answer`() {
    val holding = createHolding(id = 1L, name = "Unknown Corp", ticker = "UNK")
    every { etfHoldingPersistenceService.findUnclassifiedByCountryHoldingIds() } returns listOf(1L)
    every { etfHoldingPersistenceService.findAllByIds(listOf(1L)) } returns listOf(holding)
    every { etfHoldingPersistenceService.findEtfNamesForHoldings(listOf(1L)) } returns mapOf(1L to listOf("VWCE"))
    every { countryClassificationService.isNonCompanyHolding("Unknown Corp") } returns false
    every { countryClassificationService.classifyBatch(any()) } returns BatchClassificationOutcome(emptyMap(), false)

    runCatching { job.execute() }

    verify(exactly = 0) { etfHoldingPersistenceService.incrementCountryFetchAttempts(any()) }
    verify(exactly = 0) { etfHoldingPersistenceService.updateCountry(any(), any(), any(), any()) }
  }

  @Test
  fun `should update country and not increment attempts on successful classification`() {
    val holding = createHolding(id = 1L, name = "Apple Inc", ticker = "AAPL")
    val classificationResult = CountryClassificationResult(countryCode = "US", countryName = "United States", model = null)
    every { etfHoldingPersistenceService.findUnclassifiedByCountryHoldingIds() } returns listOf(1L)
    every { etfHoldingPersistenceService.findAllByIds(listOf(1L)) } returns listOf(holding)
    every { etfHoldingPersistenceService.findEtfNamesForHoldings(listOf(1L)) } returns mapOf(1L to listOf("VWCE"))
    every { countryClassificationService.isNonCompanyHolding("Apple Inc") } returns false
    every { countryClassificationService.classifyBatch(any()) } returns BatchClassificationOutcome(mapOf(1L to classificationResult), true)

    job.execute()

    verify(exactly = 0) { etfHoldingPersistenceService.incrementCountryFetchAttempts(any()) }
    verify(exactly = 1) { etfHoldingPersistenceService.updateCountry(1L, "US", "United States", null) }
  }

  @Test
  fun `should skip non-company holdings without incrementing attempts`() {
    val holding = createHolding(id = 1L, name = "USD Cash", ticker = null)
    every { etfHoldingPersistenceService.findUnclassifiedByCountryHoldingIds() } returns listOf(1L)
    every { etfHoldingPersistenceService.findAllByIds(listOf(1L)) } returns listOf(holding)
    every { etfHoldingPersistenceService.findEtfNamesForHoldings(listOf(1L)) } returns mapOf(1L to emptyList())
    every { countryClassificationService.isNonCompanyHolding("USD Cash") } returns true

    job.execute()

    verify(exactly = 0) { etfHoldingPersistenceService.incrementCountryFetchAttempts(any()) }
    verify(exactly = 0) { etfHoldingPersistenceService.updateCountry(any(), any(), any(), any()) }
    verify(exactly = 0) { countryClassificationService.classifyBatch(any()) }
  }

  @Test
  fun `should skip holdings with blank name without incrementing attempts`() {
    val holding = createHolding(id = 1L, name = "", ticker = "TEST")
    every { etfHoldingPersistenceService.findUnclassifiedByCountryHoldingIds() } returns listOf(1L)
    every { etfHoldingPersistenceService.findAllByIds(listOf(1L)) } returns listOf(holding)
    every { etfHoldingPersistenceService.findEtfNamesForHoldings(listOf(1L)) } returns mapOf(1L to emptyList())

    job.execute()

    verify(exactly = 0) { etfHoldingPersistenceService.incrementCountryFetchAttempts(any()) }
    verify(exactly = 0) { countryClassificationService.classifyBatch(any()) }
  }

  @Test
  fun `should do nothing when no unclassified holdings found`() {
    every { etfHoldingPersistenceService.findUnclassifiedByCountryHoldingIds() } returns emptyList()

    job.execute()

    verify(exactly = 0) { etfHoldingPersistenceService.findAllByIds(any()) }
    verify(exactly = 0) { countryClassificationService.classifyBatch(any()) }
  }

  @Test
  fun `should process multiple holdings and track attempts correctly`() {
    val holding1 = createHolding(id = 1L, name = "Success Corp", ticker = "SUC")
    val holding2 = createHolding(id = 2L, name = "Fail Corp", ticker = "FAIL")
    val successResult = CountryClassificationResult(countryCode = "DE", countryName = "Germany", model = null)
    every { etfHoldingPersistenceService.findUnclassifiedByCountryHoldingIds() } returns listOf(1L, 2L)
    every { etfHoldingPersistenceService.findAllByIds(listOf(1L, 2L)) } returns listOf(holding1, holding2)
    every { etfHoldingPersistenceService.findEtfNamesForHoldings(listOf(1L, 2L)) } returns mapOf(1L to emptyList(), 2L to emptyList())
    every { countryClassificationService.isNonCompanyHolding(any()) } returns false
    every { countryClassificationService.classifyBatch(any()) } returns BatchClassificationOutcome(mapOf(1L to successResult), true)

    job.execute()

    verify(exactly = 1) { etfHoldingPersistenceService.updateCountry(1L, "DE", "Germany", null) }
    verify(exactly = 1) { etfHoldingPersistenceService.incrementCountryFetchAttempts(2L) }
  }

  @Test
  fun `should fail job when classification produces no successes`() {
    val holding = createHolding(id = 1L, name = "Unknown Corp", ticker = "UNK")
    every { etfHoldingPersistenceService.findUnclassifiedByCountryHoldingIds() } returns listOf(1L)
    every { etfHoldingPersistenceService.findAllByIds(listOf(1L)) } returns listOf(holding)
    every { etfHoldingPersistenceService.findEtfNamesForHoldings(listOf(1L)) } returns mapOf(1L to listOf("VWCE"))
    every { countryClassificationService.isNonCompanyHolding("Unknown Corp") } returns false
    every { countryClassificationService.classifyBatch(any()) } returns BatchClassificationOutcome(emptyMap(), false)

    expect { job.execute() }.toThrow<IllegalStateException>()
  }

  @Test
  fun `should skip job entirely when classification disabled`() {
    every { properties.enabled } returns false

    job.execute()

    verify(exactly = 0) { etfHoldingPersistenceService.findUnclassifiedByCountryHoldingIds() }
  }

  @Test
  fun `should schedule runs only through daily cron trigger`() {
    val schedules = EtfCountryClassificationJob::class.java.getMethod("runJob").getAnnotationsByType(Scheduled::class.java)

    expect(schedules.map { it.cron }).toEqual(listOf("\${scheduling.jobs.etf-country-classification-cron:0 30 4 * * *}"))
  }

  @Test
  fun `should use parseable default cron expression`() {
    val schedule =
      EtfCountryClassificationJob::class.java
      .getMethod("runJob")
      .getAnnotationsByType(Scheduled::class.java)
      .single()
    val cron = schedule.cron.substringAfter(":").removeSuffix("}")

    expect(CronExpression.parse(cron).toString()).toEqual("0 30 4 * * *")
  }

  private fun createHolding(
    id: Long,
    name: String,
    ticker: String? = null,
    countryFetchAttempts: Int = 0,
  ): EtfHolding =
    EtfHolding(name = name, ticker = ticker).apply {
      this.id = id
      this.countryFetchAttempts = countryFetchAttempts
    }
}
