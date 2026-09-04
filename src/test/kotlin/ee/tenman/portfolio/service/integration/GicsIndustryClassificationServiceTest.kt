package ee.tenman.portfolio.service.integration

import ch.tutteli.atrium.api.fluent.en_GB.toContain
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.IndustryClassificationProperties
import ee.tenman.portfolio.domain.AiModel
import ee.tenman.portfolio.domain.GicsIndustry
import ee.tenman.portfolio.openrouter.OpenRouterClassificationResult
import ee.tenman.portfolio.openrouter.OpenRouterClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GicsIndustryClassificationServiceTest {
  private val openRouterClient = mockk<OpenRouterClient>()
  private val properties = mockk<IndustryClassificationProperties>()

  private lateinit var service: GicsIndustryClassificationService

  @BeforeEach
  fun setUp() {
    every { properties.enabled } returns true
    service = GicsIndustryClassificationService(openRouterClient, properties)
  }

  private fun company(
    id: Long,
    name: String,
    ticker: String? = null,
  ) = CompanyClassificationInput(holdingId = id, name = name, ticker = ticker)

  @Test
  fun `should return empty outcome for empty batch input`() {
    val outcome = service.classifyBatch(emptyList())
    expect(outcome.results.keys).toHaveSize(0)
    expect(outcome.llmAnswered).toEqual(false)
  }

  @Test
  fun `should return empty outcome when classification is disabled`() {
    every { properties.enabled } returns false

    val outcome = service.classifyBatch(listOf(company(1L, "Nvidia", "NVDA")))

    expect(outcome.llmAnswered).toEqual(false)
    verify(exactly = 0) { openRouterClient.classifyWithCascadingFallback(any(), any(), any(), any()) }
  }

  @Test
  fun `should classify a batch of three companies from returned codes`() {
    val companies = listOf(company(1L, "Nvidia", "NVDA"), company(2L, "Banco Santander", "SAN"), company(3L, "Pfizer", "PFE"))
    every { openRouterClient.classifyWithCascadingFallback(any(), AiModel.primarySectorModel()) } returns
      OpenRouterClassificationResult(content = "1. 453010\n2. 401010\n3. 352020", model = AiModel.GPT_5_6_LUNA)

    val results = service.classifyBatch(companies).results

    expect(results.keys).toHaveSize(3)
    expect(results[1L]?.industry).toEqual(GicsIndustry.SEMICONDUCTORS_AND_SEMICONDUCTOR_EQUIPMENT)
    expect(results[2L]?.industry).toEqual(GicsIndustry.BANKS)
    expect(results[3L]?.industry).toEqual(GicsIndustry.PHARMACEUTICALS)
    expect(results[1L]?.model).toEqual(AiModel.GPT_5_6_LUNA)
  }

  @Test
  fun `should request 8000 max tokens for batch classification`() {
    every { openRouterClient.classifyWithCascadingFallback(any(), any(), any(), any()) } returns
      OpenRouterClassificationResult(content = "1. 453010", model = AiModel.GPT_5_6_LUNA)

    service.classifyBatch(listOf(company(1L, "Nvidia", "NVDA")))

    verify { openRouterClient.classifyWithCascadingFallback(any(), any(), 8000, any()) }
  }

  @Test
  fun `should list every industry code in the prompt`() {
    val prompt = slot<String>()
    every { openRouterClient.classifyWithCascadingFallback(capture(prompt), any()) } returns
      OpenRouterClassificationResult(content = "1. 453010", model = AiModel.GPT_5_6_LUNA)

    service.classifyBatch(listOf(company(1L, "Nvidia", "NVDA")))

    expect(prompt.captured).toContain("201010 Aerospace & Defense")
    expect(prompt.captured).toContain("1. Nvidia (NVDA)")
  }

  @Test
  fun `should skip blank names in batch`() {
    val companies = listOf(company(1L, "Apple", "AAPL"), company(2L, "", "BLANK"), company(3L, "Microsoft", "MSFT"))
    every { openRouterClient.classifyWithCascadingFallback(any(), AiModel.primarySectorModel()) } returns
      OpenRouterClassificationResult(content = "1. 452020\n2. 451030", model = AiModel.GPT_5_6_LUNA)

    val results = service.classifyBatch(companies).results

    expect(results.keys).toHaveSize(2)
    expect(results[1L]?.industry).toEqual(GicsIndustry.TECHNOLOGY_HARDWARE_STORAGE_AND_PERIPHERALS)
    expect(results[3L]?.industry).toEqual(GicsIndustry.SOFTWARE)
    expect(results[2L]).toEqual(null)
  }

  @Test
  fun `should return partial results when response has only some lines`() {
    val companies = (1..5).map { company(it.toLong(), "Company $it", "C$it") }
    every { openRouterClient.classifyWithCascadingFallback(any(), AiModel.primarySectorModel()) } returns
      OpenRouterClassificationResult(content = "1. 453010\n3. 401010", model = AiModel.GPT_5_6_LUNA)

    val results = service.classifyBatch(companies).results

    expect(results.keys).toHaveSize(2)
    expect(results[1L]?.industry).toEqual(GicsIndustry.SEMICONDUCTORS_AND_SEMICONDUCTOR_EQUIPMENT)
    expect(results[3L]?.industry).toEqual(GicsIndustry.BANKS)
  }

  @Test
  fun `should ignore unknown codes and malformed lines`() {
    val companies = listOf(company(1L, "Nvidia", "NVDA"), company(2L, "Mystery", "XXX"), company(3L, "Pfizer", "PFE"))
    every { openRouterClient.classifyWithCascadingFallback(any(), AiModel.primarySectorModel()) } returns
      OpenRouterClassificationResult(content = "1. 453010\n2. 999999\nthree: Pharmaceuticals\n4. 352020", model = AiModel.GPT_5_6_LUNA)

    val outcome = service.classifyBatch(companies)

    expect(outcome.llmAnswered).toEqual(true)
    expect(outcome.results.keys).toEqual(setOf(1L))
  }

  @Test
  fun `should accept a code followed by its name`() {
    every { openRouterClient.classifyWithCascadingFallback(any(), AiModel.primarySectorModel()) } returns
      OpenRouterClassificationResult(content = "1. 201010 Aerospace & Defense", model = AiModel.GPT_5_6_LUNA)

    val results = service.classifyBatch(listOf(company(1L, "Rheinmetall", "RHM"))).results

    expect(results[1L]?.industry).toEqual(GicsIndustry.AEROSPACE_AND_DEFENSE)
  }

  @Test
  fun `should return empty outcome when all fallback models fail`() {
    every { openRouterClient.classifyWithCascadingFallback(any(), AiModel.primarySectorModel()) } returns null

    val outcome = service.classifyBatch(listOf(company(1L, "Apple", "AAPL")))

    expect(outcome.results.keys).toHaveSize(0)
    expect(outcome.llmAnswered).toEqual(false)
  }
}
