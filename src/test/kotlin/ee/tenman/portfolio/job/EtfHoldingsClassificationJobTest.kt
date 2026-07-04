package ee.tenman.portfolio.job

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.IndustryClassificationProperties
import ee.tenman.portfolio.domain.AiModel
import ee.tenman.portfolio.domain.EtfHolding
import ee.tenman.portfolio.domain.IndustrySector
import ee.tenman.portfolio.openrouter.OpenRouterCircuitBreaker
import ee.tenman.portfolio.service.etf.EtfHoldingPersistenceService
import ee.tenman.portfolio.service.infrastructure.CacheInvalidationService
import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import ee.tenman.portfolio.service.integration.BatchClassificationOutcome
import ee.tenman.portfolio.service.integration.CompanyClassificationInput
import ee.tenman.portfolio.service.integration.IndustryClassificationService
import ee.tenman.portfolio.service.integration.SectorClassificationResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.scheduling.support.CronExpression

class EtfHoldingsClassificationJobTest {
  private val etfHoldingPersistenceService: EtfHoldingPersistenceService = mockk(relaxed = true)
  private val industryClassificationService: IndustryClassificationService = mockk()
  private val jobExecutionService: JobExecutionService = mockk(relaxed = true)
  private val circuitBreaker: OpenRouterCircuitBreaker = mockk()
  private val properties: IndustryClassificationProperties = mockk(relaxed = true)
  private val cacheInvalidationService: CacheInvalidationService = mockk(relaxed = true)

  private lateinit var job: EtfHoldingsClassificationJob

  @BeforeEach
  fun setup() {
    every { circuitBreaker.getWaitTimeMs(any()) } returns 0L
    every { circuitBreaker.isUsingFallback() } returns false
    every { properties.rateLimitBufferMs } returns 100L
    every { properties.enabled } returns true
    job =
      EtfHoldingsClassificationJob(
        etfHoldingPersistenceService = etfHoldingPersistenceService,
        industryClassificationService = industryClassificationService,
        jobExecutionService = jobExecutionService,
        circuitBreaker = circuitBreaker,
        properties = properties,
        cacheInvalidationService = cacheInvalidationService,
      )
  }

  private fun createHolding(
    id: Long,
    name: String,
    ticker: String? = null,
  ): EtfHolding = EtfHolding(name = name, ticker = ticker).apply { this.id = id }

  @Test
  fun `should do nothing when no unclassified holdings found`() {
    every { etfHoldingPersistenceService.findUnclassifiedHoldingIds() } returns emptyList()

    job.execute()

    verify(exactly = 0) { industryClassificationService.classifyBatch(any()) }
  }

  @Test
  fun `should process holdings in batches of 100`() {
    val ids = (1L..250L).toList()
    val holdings = ids.map { createHolding(it, "Co $it", "T$it") }
    every { etfHoldingPersistenceService.findUnclassifiedHoldingIds() } returns ids
    every { etfHoldingPersistenceService.findAllByIds(ids) } returns holdings
    val batchSlot = slot<List<CompanyClassificationInput>>()
    val invokedSizes: MutableList<Int> = mutableListOf()
    every { industryClassificationService.classifyBatch(capture(batchSlot)) } answers {
      invokedSizes.add(batchSlot.captured.size)
      BatchClassificationOutcome(
        batchSlot.captured.associate { it.holdingId to SectorClassificationResult(sector = IndustrySector.FINANCE, model = null) },
        true,
      )
    }

    job.execute()

    expect(invokedSizes as List<Int>).toEqual(listOf(100, 100, 50))
  }

  @Test
  fun `should call updateSector for each successfully classified holding`() {
    val holding1 = createHolding(1L, "Apple Inc", "AAPL")
    val holding2 = createHolding(2L, "JPMorgan", "JPM")
    every { etfHoldingPersistenceService.findUnclassifiedHoldingIds() } returns listOf(1L, 2L)
    every { etfHoldingPersistenceService.findAllByIds(listOf(1L, 2L)) } returns listOf(holding1, holding2)
    every { industryClassificationService.classifyBatch(any()) } returns
      BatchClassificationOutcome(
        mapOf(
          1L to SectorClassificationResult(sector = IndustrySector.SEMICONDUCTORS, model = AiModel.GEMINI_3_FLASH_PREVIEW),
          2L to SectorClassificationResult(sector = IndustrySector.FINANCE, model = AiModel.GEMINI_3_FLASH_PREVIEW),
        ),
        true,
      )

    job.execute()

    verify(exactly = 1) { etfHoldingPersistenceService.updateSector(1L, IndustrySector.SEMICONDUCTORS, AiModel.GEMINI_3_FLASH_PREVIEW) }
    verify(exactly = 1) { etfHoldingPersistenceService.updateSector(2L, IndustrySector.FINANCE, AiModel.GEMINI_3_FLASH_PREVIEW) }
  }

  @Test
  fun `should skip updateSector for holdings missing from batch results`() {
    val holding1 = createHolding(1L, "Apple", "AAPL")
    val holding2 = createHolding(2L, "Mystery", "XXX")
    every { etfHoldingPersistenceService.findUnclassifiedHoldingIds() } returns listOf(1L, 2L)
    every { etfHoldingPersistenceService.findAllByIds(listOf(1L, 2L)) } returns listOf(holding1, holding2)
    every { industryClassificationService.classifyBatch(any()) } returns
      BatchClassificationOutcome(
        mapOf(1L to SectorClassificationResult(sector = IndustrySector.SEMICONDUCTORS, model = AiModel.GEMINI_3_FLASH_PREVIEW)),
        true,
      )

    job.execute()

    verify(exactly = 1) { etfHoldingPersistenceService.updateSector(1L, IndustrySector.SEMICONDUCTORS, AiModel.GEMINI_3_FLASH_PREVIEW) }
    verify(exactly = 0) { etfHoldingPersistenceService.updateSector(2L, any(), any()) }
  }

  @Test
  fun `should skip holdings with blank name`() {
    val holding1 = createHolding(1L, "Apple", "AAPL")
    val holding2 = createHolding(2L, "", "BLANK")
    every { etfHoldingPersistenceService.findUnclassifiedHoldingIds() } returns listOf(1L, 2L)
    every { etfHoldingPersistenceService.findAllByIds(listOf(1L, 2L)) } returns listOf(holding1, holding2)
    val batchSlot = slot<List<CompanyClassificationInput>>()
    every { industryClassificationService.classifyBatch(capture(batchSlot)) } answers {
      BatchClassificationOutcome(
        batchSlot.captured.associate { it.holdingId to SectorClassificationResult(sector = IndustrySector.FINANCE, model = null) },
        true,
      )
    }

    job.execute()

    expect(batchSlot.captured.map { it.holdingId }).toEqual(listOf(1L))
  }

  @Test
  fun `should evict etf breakdown caches when classification succeeds`() {
    val holding = createHolding(1L, "Apple Inc", "AAPL")
    every { etfHoldingPersistenceService.findUnclassifiedHoldingIds() } returns listOf(1L)
    every { etfHoldingPersistenceService.findAllByIds(listOf(1L)) } returns listOf(holding)
    every { industryClassificationService.classifyBatch(any()) } returns
      BatchClassificationOutcome(
        mapOf(1L to SectorClassificationResult(sector = IndustrySector.SEMICONDUCTORS, model = AiModel.GEMINI_3_FLASH_PREVIEW)),
        true,
      )

    job.execute()

    verify(exactly = 1) { cacheInvalidationService.evictEtfBreakdownCache() }
    verify(exactly = 1) { cacheInvalidationService.evictDiversificationEtfsCache() }
  }

  @Test
  fun `should not evict caches when no holdings classified`() {
    val holding = createHolding(1L, "Apple Inc", "AAPL")
    every { etfHoldingPersistenceService.findUnclassifiedHoldingIds() } returns listOf(1L)
    every { etfHoldingPersistenceService.findAllByIds(listOf(1L)) } returns listOf(holding)
    every { industryClassificationService.classifyBatch(any()) } returns BatchClassificationOutcome(emptyMap(), false)

    runCatching { job.execute() }

    verify(exactly = 0) { cacheInvalidationService.evictEtfBreakdownCache() }
  }

  @Test
  fun `should increment sector fetch attempts only for holdings missing from an answered batch`() {
    val holding1 = createHolding(1L, "Apple Inc", "AAPL")
    val holding2 = createHolding(2L, "Mystery Corp", "XXX")
    every { etfHoldingPersistenceService.findUnclassifiedHoldingIds() } returns listOf(1L, 2L)
    every { etfHoldingPersistenceService.findAllByIds(listOf(1L, 2L)) } returns listOf(holding1, holding2)
    every { industryClassificationService.classifyBatch(any()) } returns
      BatchClassificationOutcome(
        mapOf(1L to SectorClassificationResult(sector = IndustrySector.SEMICONDUCTORS, model = AiModel.GEMINI_3_FLASH_PREVIEW)),
        true,
      )

    job.execute()

    verify(exactly = 1) { etfHoldingPersistenceService.incrementSectorFetchAttempts(2L) }
    verify(exactly = 0) { etfHoldingPersistenceService.incrementSectorFetchAttempts(1L) }
  }

  @Test
  fun `should not increment sector fetch attempts when classification succeeds`() {
    val holding = createHolding(1L, "Apple Inc", "AAPL")
    every { etfHoldingPersistenceService.findUnclassifiedHoldingIds() } returns listOf(1L)
    every { etfHoldingPersistenceService.findAllByIds(listOf(1L)) } returns listOf(holding)
    every { industryClassificationService.classifyBatch(any()) } returns
      BatchClassificationOutcome(
        mapOf(1L to SectorClassificationResult(sector = IndustrySector.SEMICONDUCTORS, model = AiModel.GEMINI_3_FLASH_PREVIEW)),
        true,
      )

    job.execute()

    verify(exactly = 0) { etfHoldingPersistenceService.incrementSectorFetchAttempts(any()) }
  }

  @Test
  fun `should have correct job name`() {
    expect(job.getName()).toEqual("EtfHoldingsClassificationJob")
  }

  @Test
  fun `cannot increment sector fetch attempts when model gives no answer`() {
    val holding = createHolding(1L, "Mystery Corp", "XXX")
    every { etfHoldingPersistenceService.findUnclassifiedHoldingIds() } returns listOf(1L)
    every { etfHoldingPersistenceService.findAllByIds(listOf(1L)) } returns listOf(holding)
    every { industryClassificationService.classifyBatch(any()) } returns BatchClassificationOutcome(emptyMap(), false)

    runCatching { job.execute() }

    verify(exactly = 0) { etfHoldingPersistenceService.incrementSectorFetchAttempts(any()) }
  }

  @Test
  fun `should fail job when classification produces no successes`() {
    val holding = createHolding(1L, "Mystery Corp", "XXX")
    every { etfHoldingPersistenceService.findUnclassifiedHoldingIds() } returns listOf(1L)
    every { etfHoldingPersistenceService.findAllByIds(listOf(1L)) } returns listOf(holding)
    every { industryClassificationService.classifyBatch(any()) } returns BatchClassificationOutcome(emptyMap(), false)

    expect { job.execute() }.toThrow<IllegalStateException>()
  }

  @Test
  fun `should skip job entirely when classification disabled`() {
    every { properties.enabled } returns false

    job.execute()

    verify(exactly = 0) { etfHoldingPersistenceService.findUnclassifiedHoldingIds() }
  }

  @Test
  fun `should skip holdings with blank name without incrementing attempts`() {
    val holding1 = createHolding(1L, "Apple", "AAPL")
    val holding2 = createHolding(2L, "", "BLANK")
    every { etfHoldingPersistenceService.findUnclassifiedHoldingIds() } returns listOf(1L, 2L)
    every { etfHoldingPersistenceService.findAllByIds(listOf(1L, 2L)) } returns listOf(holding1, holding2)
    every { industryClassificationService.classifyBatch(any()) } answers {
      val inputs = firstArg<List<CompanyClassificationInput>>()
      BatchClassificationOutcome(
        inputs.associate { it.holdingId to SectorClassificationResult(sector = IndustrySector.FINANCE, model = null) },
        true,
      )
    }

    job.execute()

    verify(exactly = 0) { etfHoldingPersistenceService.incrementSectorFetchAttempts(any()) }
  }

  @Test
  fun `should schedule runs only through weekly cron trigger`() {
    val schedules = EtfHoldingsClassificationJob::class.java.getMethod("runJob").getAnnotationsByType(Scheduled::class.java)

    expect(schedules.map { it.cron }).toEqual(listOf("\${scheduling.jobs.etf-holdings-classification-cron:0 0 3 * * SUN}"))
  }

  @Test
  fun `should use parseable default cron expression`() {
    val schedule =
      EtfHoldingsClassificationJob::class.java
      .getMethod("runJob")
      .getAnnotationsByType(Scheduled::class.java)
      .single()
    val cron = schedule.cron.substringAfter(":").removeSuffix("}")

    expect(CronExpression.parse(cron).toString()).toEqual("0 0 3 * * SUN")
  }
}
