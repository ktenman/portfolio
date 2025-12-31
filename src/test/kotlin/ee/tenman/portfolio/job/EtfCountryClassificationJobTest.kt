package ee.tenman.portfolio.job

import ee.tenman.portfolio.configuration.IndustryClassificationProperties
import ee.tenman.portfolio.domain.EtfHolding
import ee.tenman.portfolio.openrouter.OpenRouterCircuitBreaker
import ee.tenman.portfolio.service.etf.EtfHoldingPersistenceService
import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import ee.tenman.portfolio.service.integration.CountryClassificationResult
import ee.tenman.portfolio.service.integration.CountryClassificationService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

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
  fun `should increment attempts when classification returns null`() {
    val holding = createHolding(id = 1L, name = "Unknown Corp", ticker = "UNK")
    every { etfHoldingPersistenceService.findUnclassifiedByCountryHoldingIds() } returns listOf(1L)
    every { etfHoldingPersistenceService.findAllByIds(listOf(1L)) } returns listOf(holding)
    every { etfHoldingPersistenceService.findEtfNamesForHoldings(listOf(1L)) } returns mapOf(1L to listOf("VWCE"))
    every { countryClassificationService.isNonCompanyHolding("Unknown Corp") } returns false
    every { countryClassificationService.classifyCompanyCountryWithModel("Unknown Corp", "UNK", listOf("VWCE")) } returns null

    job.execute()

    verify(exactly = 1) { etfHoldingPersistenceService.incrementCountryFetchAttempts(1L) }
    verify(exactly = 0) { etfHoldingPersistenceService.updateCountry(any(), any(), any(), any()) }
  }

  @Test
  fun `should increment attempts when exception occurs during classification`() {
    val holding = createHolding(id = 1L, name = "Error Corp", ticker = "ERR")
    every { etfHoldingPersistenceService.findUnclassifiedByCountryHoldingIds() } returns listOf(1L)
    every { etfHoldingPersistenceService.findAllByIds(listOf(1L)) } returns listOf(holding)
    every { etfHoldingPersistenceService.findEtfNamesForHoldings(listOf(1L)) } returns mapOf(1L to listOf("VWCE"))
    every { countryClassificationService.isNonCompanyHolding("Error Corp") } returns false
    every {
      countryClassificationService.classifyCompanyCountryWithModel("Error Corp", "ERR", listOf("VWCE"))
    } throws RuntimeException("API error")

    job.execute()

    verify(exactly = 1) { etfHoldingPersistenceService.incrementCountryFetchAttempts(1L) }
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
    every { countryClassificationService.classifyCompanyCountryWithModel("Apple Inc", "AAPL", listOf("VWCE")) } returns classificationResult

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
    verify(exactly = 0) { countryClassificationService.classifyCompanyCountryWithModel(any(), any(), any()) }
  }

  @Test
  fun `should skip holdings with blank name without incrementing attempts`() {
    val holding = createHolding(id = 1L, name = "", ticker = "TEST")
    every { etfHoldingPersistenceService.findUnclassifiedByCountryHoldingIds() } returns listOf(1L)
    every { etfHoldingPersistenceService.findAllByIds(listOf(1L)) } returns listOf(holding)
    every { etfHoldingPersistenceService.findEtfNamesForHoldings(listOf(1L)) } returns mapOf(1L to emptyList())

    job.execute()

    verify(exactly = 0) { etfHoldingPersistenceService.incrementCountryFetchAttempts(any()) }
    verify(exactly = 0) { countryClassificationService.classifyCompanyCountryWithModel(any(), any(), any()) }
  }

  @Test
  fun `should do nothing when no unclassified holdings found`() {
    every { etfHoldingPersistenceService.findUnclassifiedByCountryHoldingIds() } returns emptyList()

    job.execute()

    verify(exactly = 0) { etfHoldingPersistenceService.findAllByIds(any()) }
    verify(exactly = 0) { countryClassificationService.classifyCompanyCountryWithModel(any(), any(), any()) }
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
    every { countryClassificationService.classifyCompanyCountryWithModel("Success Corp", "SUC", emptyList()) } returns successResult
    every { countryClassificationService.classifyCompanyCountryWithModel("Fail Corp", "FAIL", emptyList()) } returns null

    job.execute()

    verify(exactly = 1) { etfHoldingPersistenceService.updateCountry(1L, "DE", "Germany", null) }
    verify(exactly = 1) { etfHoldingPersistenceService.incrementCountryFetchAttempts(2L) }
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
