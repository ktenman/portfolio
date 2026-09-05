package ee.tenman.portfolio.job

import ch.tutteli.atrium.api.fluent.en_GB.notToThrow
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.IndustryClassificationProperties
import ee.tenman.portfolio.domain.AiModel
import ee.tenman.portfolio.domain.EtfHolding
import ee.tenman.portfolio.domain.GicsIndustry
import ee.tenman.portfolio.openrouter.OpenRouterCircuitBreaker
import ee.tenman.portfolio.service.etf.EtfHoldingIndustryService
import ee.tenman.portfolio.service.infrastructure.CacheInvalidationService
import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import ee.tenman.portfolio.service.integration.BatchClassificationOutcome
import ee.tenman.portfolio.service.integration.CompanyClassificationInput
import ee.tenman.portfolio.service.integration.GicsIndustryClassificationResult
import ee.tenman.portfolio.service.integration.GicsIndustryClassificationService
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.scheduling.support.CronExpression

class EtfIndustryClassificationJobTest {
  private val etfHoldingIndustryService: EtfHoldingIndustryService = mockk(relaxed = true)
  private val classificationService: GicsIndustryClassificationService = mockk()
  private val jobExecutionService: JobExecutionService = mockk(relaxed = true)
  private val circuitBreaker: OpenRouterCircuitBreaker = mockk()
  private val properties: IndustryClassificationProperties = mockk(relaxed = true)
  private val cacheInvalidationService: CacheInvalidationService = mockk(relaxed = true)

  private lateinit var job: EtfIndustryClassificationJob

  @BeforeEach
  fun setup() {
    every { circuitBreaker.getWaitTimeMs(any()) } returns 0L
    every { circuitBreaker.isUsingFallback() } returns false
    every { properties.rateLimitBufferMs } returns 100L
    every { properties.enabled } returns true
    job =
      EtfIndustryClassificationJob(
        etfHoldingIndustryService = etfHoldingIndustryService,
        classificationService = classificationService,
        jobExecutionService = jobExecutionService,
        circuitBreaker = circuitBreaker,
        properties = properties,
        cacheInvalidationService = cacheInvalidationService,
      )
  }

  private fun holding(
    id: Long,
    name: String,
    ticker: String? = null,
  ): EtfHolding = EtfHolding(name = name, ticker = ticker).apply { this.id = id }

  private fun unclassified(vararg holdings: EtfHolding) {
    every { etfHoldingIndustryService.findUnclassifiedByIndustry() } returns holdings.toList()
  }

  private fun answered(vararg pairs: Pair<Long, GicsIndustry>) =
    BatchClassificationOutcome(
      pairs.associate { it.first to GicsIndustryClassificationResult(it.second, AiModel.GPT_5_6_LUNA) },
      true,
    )

  @Test
  fun `should do nothing when no unclassified holdings found`() {
    unclassified()

    job.execute()

    verify(exactly = 0) { classificationService.classifyBatch(any()) }
  }

  @Test
  fun `should process holdings in batches of 100`() {
    unclassified(*(1L..250L).map { holding(it, "Co $it", "T$it") }.toTypedArray())
    val batches = mutableListOf<List<CompanyClassificationInput>>()
    every { classificationService.classifyBatch(capture(batches)) } answers {
      answered(*batches.last().map { it.holdingId to GicsIndustry.BANKS }.toTypedArray())
    }

    job.execute()

    expect(batches.map { it.size }).toEqual(listOf(100, 100, 50))
  }

  @Test
  fun `should call updateIndustry for each successfully classified holding`() {
    unclassified(holding(1L, "Nvidia", "NVDA"), holding(2L, "Rheinmetall", "RHM"))
    every { classificationService.classifyBatch(any()) } returns
      answered(1L to GicsIndustry.SEMICONDUCTORS_AND_SEMICONDUCTOR_EQUIPMENT, 2L to GicsIndustry.AEROSPACE_AND_DEFENSE)

    job.execute()

    verify(exactly = 1) {
      etfHoldingIndustryService.updateIndustry(1L, GicsIndustry.SEMICONDUCTORS_AND_SEMICONDUCTOR_EQUIPMENT, AiModel.GPT_5_6_LUNA)
    }
    verify(exactly = 1) {
      etfHoldingIndustryService.updateIndustry(2L, GicsIndustry.AEROSPACE_AND_DEFENSE, AiModel.GPT_5_6_LUNA)
    }
  }

  @Test
  fun `should increment industry fetch attempts only for holdings missing from an answered batch`() {
    unclassified(holding(1L, "Nvidia", "NVDA"), holding(2L, "Mystery Corp", "XXX"))
    every { classificationService.classifyBatch(any()) } returns
      answered(1L to GicsIndustry.SEMICONDUCTORS_AND_SEMICONDUCTOR_EQUIPMENT)

    job.execute()

    verify(exactly = 1) { etfHoldingIndustryService.incrementIndustryFetchAttempts(2L) }
    verify(exactly = 0) { etfHoldingIndustryService.incrementIndustryFetchAttempts(1L) }
  }

  @Test
  fun `cannot increment industry fetch attempts when model gives no answer`() {
    unclassified(holding(1L, "Mystery Corp", "XXX"))
    every { classificationService.classifyBatch(any()) } returns BatchClassificationOutcome(emptyMap(), false)

    runCatching { job.execute() }

    verify(exactly = 0) { etfHoldingIndustryService.incrementIndustryFetchAttempts(any()) }
  }

  @Test
  fun `should skip holdings with blank name without incrementing attempts`() {
    unclassified(holding(1L, "Nvidia", "NVDA"), holding(2L, "", "BLANK"))
    val batch = slot<List<CompanyClassificationInput>>()
    every { classificationService.classifyBatch(capture(batch)) } returns
      answered(1L to GicsIndustry.SEMICONDUCTORS_AND_SEMICONDUCTOR_EQUIPMENT)

    job.execute()

    expect(batch.captured.map { it.holdingId }).toEqual(listOf(1L))
    verify(exactly = 0) { etfHoldingIndustryService.incrementIndustryFetchAttempts(2L) }
  }

  @Test
  fun `should evict etf breakdown caches when classification succeeds`() {
    unclassified(holding(1L, "Nvidia", "NVDA"))
    every { classificationService.classifyBatch(any()) } returns
      answered(1L to GicsIndustry.SEMICONDUCTORS_AND_SEMICONDUCTOR_EQUIPMENT)

    job.execute()

    verify(exactly = 1) { cacheInvalidationService.evictEtfBreakdownCache() }
    verify(exactly = 1) { cacheInvalidationService.evictDiversificationEtfsCache() }
  }

  @Test
  fun `should not evict caches when no holdings classified`() {
    unclassified()

    job.execute()

    verify(exactly = 0) { cacheInvalidationService.evictEtfBreakdownCache() }
  }

  @Test
  fun `should fail job when classification produces no successes`() {
    unclassified(holding(1L, "Mystery Corp", "XXX"))
    every { classificationService.classifyBatch(any()) } returns BatchClassificationOutcome(emptyMap(), false)

    expect { job.execute() }.toThrow<IllegalStateException>()
  }

  @Test
  fun `cannot fail job when every holding is skipped for blank name`() {
    unclassified(holding(1L, "", "BLANK"))

    expect { job.execute() }.notToThrow()
  }

  @Test
  fun `should skip job entirely when classification disabled`() {
    every { properties.enabled } returns false

    job.execute()

    verify(exactly = 0) { etfHoldingIndustryService.findUnclassifiedByIndustry() }
  }

  @Test
  fun `should have correct job name`() {
    expect(job.getName()).toEqual("EtfIndustryClassificationJob")
  }

  @Test
  fun `should schedule runs through boot trigger and weekly cron trigger with parseable default`() {
    val annotations =
      EtfIndustryClassificationJob::class.java
        .getMethod("runJob")
        .getAnnotationsByType(Scheduled::class.java)
        .toList()
    expect(annotations).toHaveSize(2)
    val cronTrigger = annotations.first { it.cron.isNotEmpty() }
    CronExpression.parse(cronTrigger.cron.substringAfter(":").removeSuffix("}"))
    expect(cronTrigger.cron).toEqual("\${scheduling.jobs.etf-industry-classification-cron:0 0 4 * * SUN}")
    val bootTrigger = annotations.first { it.cron.isEmpty() }
    expect(bootTrigger.initialDelay).toEqual(240000L)
    expect(bootTrigger.fixedDelay).toEqual(Long.MAX_VALUE)
  }
}
