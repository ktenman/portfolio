package ee.tenman.portfolio.service.integration

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.IndustryClassificationProperties
import ee.tenman.portfolio.domain.AiModel
import ee.tenman.portfolio.domain.IndustrySector
import ee.tenman.portfolio.openrouter.OpenRouterClassificationResult
import ee.tenman.portfolio.openrouter.OpenRouterClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class IndustryClassificationServiceTest {
  private val openRouterClient = mockk<OpenRouterClient>()
  private val properties = mockk<IndustryClassificationProperties>()

  private lateinit var service: IndustryClassificationService

  @BeforeEach
  fun setUp() {
    service = IndustryClassificationService(openRouterClient, properties)
  }

  @Test
  fun `should return null when classification is disabled`() {
    every { properties.enabled } returns false

    val result = service.classifyCompanyWithModel("Apple Inc")

    expect(result).toEqual(null)
    verify(exactly = 0) { openRouterClient.classifyWithModel(any()) }
  }

  @Test
  fun `should return null when company name is blank`() {
    every { properties.enabled } returns true

    val result = service.classifyCompanyWithModel("   ")

    expect(result).toEqual(null)
    verify(exactly = 0) { openRouterClient.classifyWithModel(any()) }
  }

  @Test
  fun `should return null when OpenRouter returns no response`() {
    every { properties.enabled } returns true
    every { openRouterClient.classifyWithModel(any()) } returns null
    every { openRouterClient.classifyWithCascadingFallback(any(), any(), any(), any()) } returns null

    val result = service.classifyCompanyWithModel("Apple Inc")

    expect(result).toEqual(null)
  }

  @Test
  fun `should return null when OpenRouter response has null content`() {
    every { properties.enabled } returns true
    every { openRouterClient.classifyWithModel(any()) } returns
      OpenRouterClassificationResult(content = null, model = AiModel.GEMINI_3_FLASH_PREVIEW)
    every { openRouterClient.classifyWithCascadingFallback(any(), any(), any(), any()) } returns null

    val result = service.classifyCompanyWithModel("Apple Inc")

    expect(result).toEqual(null)
  }

  @Test
  fun `should retry with cascading fallback when primary model returns unknown sector`() {
    every { properties.enabled } returns true
    every { openRouterClient.classifyWithModel(any()) } returns
      OpenRouterClassificationResult(content = "Unknown Category", model = AiModel.GEMINI_3_FLASH_PREVIEW)
    every { openRouterClient.classifyWithCascadingFallback(any(), AiModel.CLAUDE_SONNET_4_6, any(), any()) } returns
      OpenRouterClassificationResult(content = "Software & Cloud Services", model = AiModel.CLAUDE_SONNET_4_6)

    val result = service.classifyCompanyWithModel("Apple Inc")

    expect(result?.sector).toEqual(IndustrySector.SOFTWARE_CLOUD_SERVICES)
    expect(result?.model).toEqual(AiModel.CLAUDE_SONNET_4_6)
    verify(exactly = 1) { openRouterClient.classifyWithModel(any()) }
    verify(exactly = 1) { openRouterClient.classifyWithCascadingFallback(any(), AiModel.CLAUDE_SONNET_4_6, any(), any()) }
  }

  @Test
  fun `should return null when all cascading fallbacks return unknown sector`() {
    every { properties.enabled } returns true
    every { openRouterClient.classifyWithModel(any()) } returns
      OpenRouterClassificationResult(content = "Unknown Category", model = AiModel.GEMINI_3_FLASH_PREVIEW)
    every { openRouterClient.classifyWithCascadingFallback(any(), any(), any(), any()) } returns
      OpenRouterClassificationResult(content = "Still Unknown", model = AiModel.DEEPSEEK_V3_2)

    val result = service.classifyCompanyWithModel("Apple Inc")

    expect(result).toEqual(null)
  }

  @Test
  fun `should cascade to next model when claude sonnet returns unknown sector`() {
    every { properties.enabled } returns true
    every { openRouterClient.classifyWithModel(any()) } returns
      OpenRouterClassificationResult(content = "Unknown Category", model = AiModel.CLAUDE_SONNET_4_6)
    every { openRouterClient.classifyWithCascadingFallback(any(), AiModel.DEEPSEEK_V3_2, any(), any()) } returns
      OpenRouterClassificationResult(content = "Software & Cloud Services", model = AiModel.DEEPSEEK_V3_2)

    val result = service.classifyCompanyWithModel("Apple Inc")

    expect(result?.sector).toEqual(IndustrySector.SOFTWARE_CLOUD_SERVICES)
    expect(result?.model).toEqual(AiModel.DEEPSEEK_V3_2)
  }

  @Test
  fun `should return null when cascading fallback returns no response`() {
    every { properties.enabled } returns true
    every { openRouterClient.classifyWithModel(any()) } returns
      OpenRouterClassificationResult(content = "Unknown Category", model = AiModel.GEMINI_3_FLASH_PREVIEW)
    every { openRouterClient.classifyWithCascadingFallback(any(), any(), any(), any()) } returns null

    val result = service.classifyCompanyWithModel("Apple Inc")

    expect(result).toEqual(null)
  }

  @Test
  fun `should use DEEPSEEK_V3_2 as cascading fallback after CLAUDE_SONNET_4_6 fails`() {
    every { properties.enabled } returns true
    every { openRouterClient.classifyWithModel(any()) } returns
      OpenRouterClassificationResult(content = "Unknown", model = AiModel.CLAUDE_SONNET_4_6)
    every { openRouterClient.classifyWithCascadingFallback(any(), AiModel.DEEPSEEK_V3_2, any(), any()) } returns
      OpenRouterClassificationResult(content = "Semiconductors", model = AiModel.DEEPSEEK_V3_2)

    val result = service.classifyCompanyWithModel("Nvidia")

    expect(result?.sector).toEqual(IndustrySector.SEMICONDUCTORS)
    expect(result?.model).toEqual(AiModel.DEEPSEEK_V3_2)
    verify(exactly = 1) { openRouterClient.classifyWithCascadingFallback(any(), AiModel.DEEPSEEK_V3_2, any(), any()) }
  }

  @Test
  fun `should cascade from DEEPSEEK_V3_2 to GPT_5_4 when DEEPSEEK returns unknown sector`() {
    every { properties.enabled } returns true
    every { openRouterClient.classifyWithModel(any()) } returns
      OpenRouterClassificationResult(content = "Unknown", model = AiModel.DEEPSEEK_V3_2)
    every { openRouterClient.classifyWithCascadingFallback(any(), AiModel.GPT_5_4, any(), any()) } returns
      OpenRouterClassificationResult(content = "Finance", model = AiModel.GPT_5_4)

    val result = service.classifyCompanyWithModel("JPMorgan")

    expect(result?.sector).toEqual(IndustrySector.FINANCE)
    expect(result?.model).toEqual(AiModel.GPT_5_4)
  }

  @Test
  fun `should return sector classification result for valid response`() {
    every { properties.enabled } returns true
    every { openRouterClient.classifyWithModel(any()) } returns
      OpenRouterClassificationResult(content = "Semiconductors", model = AiModel.GEMINI_3_FLASH_PREVIEW)

    val result = service.classifyCompanyWithModel("Nvidia")

    expect(result?.sector).toEqual(IndustrySector.SEMICONDUCTORS)
    expect(result?.model).toEqual(AiModel.GEMINI_3_FLASH_PREVIEW)
  }

  @Test
  fun `should return sector classification result with fallback model`() {
    every { properties.enabled } returns true
    every { openRouterClient.classifyWithModel(any()) } returns
      OpenRouterClassificationResult(content = "Finance", model = AiModel.CLAUDE_OPUS_4_6)

    val result = service.classifyCompanyWithModel("JPMorgan Chase")

    expect(result?.sector).toEqual(IndustrySector.FINANCE)
    expect(result?.model).toEqual(AiModel.CLAUDE_OPUS_4_6)
  }

  @Test
  fun `should return only sector when using classifyCompany`() {
    every { properties.enabled } returns true
    every { openRouterClient.classifyWithModel(any()) } returns
      OpenRouterClassificationResult(content = "Health", model = AiModel.GEMINI_3_FLASH_PREVIEW)

    val result = service.classifyCompany("Pfizer")

    expect(result).toEqual(IndustrySector.HEALTH)
  }

  @Test
  fun `should return null from classifyCompany when classification fails`() {
    every { properties.enabled } returns true
    every { openRouterClient.classifyWithModel(any()) } returns null
    every { openRouterClient.classifyWithCascadingFallback(any(), any(), any(), any()) } returns null

    val result = service.classifyCompany("Unknown Corp")

    expect(result).toEqual(null)
  }

  @Test
  fun `should return empty map for empty batch input`() {
    val result = service.classifyBatch(emptyList())
    expect(result.keys).toHaveSize(0)
  }

  @Test
  fun `should classify batch of 3 companies`() {
    every { properties.enabled } returns true
    val companies =
      listOf(
        CompanyClassificationInput(holdingId = 1L, name = "Nvidia", ticker = "NVDA"),
        CompanyClassificationInput(holdingId = 2L, name = "JPMorgan", ticker = "JPM"),
        CompanyClassificationInput(holdingId = 3L, name = "Pfizer", ticker = "PFE"),
      )
    every { openRouterClient.classifyWithCascadingFallback(any(), AiModel.GEMINI_3_FLASH_PREVIEW) } returns
      OpenRouterClassificationResult(
        content = "1. Semiconductors\n2. Finance\n3. Health",
        model = AiModel.GEMINI_3_FLASH_PREVIEW,
      )

    val result = service.classifyBatch(companies)

    expect(result.keys).toHaveSize(3)
    expect(result[1L]!!.sector).toEqual(IndustrySector.SEMICONDUCTORS)
    expect(result[2L]!!.sector).toEqual(IndustrySector.FINANCE)
    expect(result[3L]!!.sector).toEqual(IndustrySector.HEALTH)
  }

  @Test
  fun `should skip blank names in batch`() {
    every { properties.enabled } returns true
    val companies =
      listOf(
        CompanyClassificationInput(holdingId = 1L, name = "Apple", ticker = "AAPL"),
        CompanyClassificationInput(holdingId = 2L, name = "", ticker = "BLANK"),
        CompanyClassificationInput(holdingId = 3L, name = "Microsoft", ticker = "MSFT"),
      )
    every { openRouterClient.classifyWithCascadingFallback(any(), AiModel.GEMINI_3_FLASH_PREVIEW) } returns
      OpenRouterClassificationResult(
        content = "1. Semiconductors\n2. Software & Cloud Services",
        model = AiModel.GEMINI_3_FLASH_PREVIEW,
      )

    val result = service.classifyBatch(companies)

    expect(result.keys).toHaveSize(2)
    expect(result[1L]!!.sector).toEqual(IndustrySector.SEMICONDUCTORS)
    expect(result[3L]!!.sector).toEqual(IndustrySector.SOFTWARE_CLOUD_SERVICES)
    expect(result[2L]).toEqual(null)
  }

  @Test
  fun `should return partial results when response has only some lines`() {
    every { properties.enabled } returns true
    val companies =
      (1..5).map {
        CompanyClassificationInput(holdingId = it.toLong(), name = "Company $it", ticker = "C$it")
      }
    every { openRouterClient.classifyWithCascadingFallback(any(), AiModel.GEMINI_3_FLASH_PREVIEW) } returns
      OpenRouterClassificationResult(
        content = "1. Semiconductors\n3. Finance",
        model = AiModel.GEMINI_3_FLASH_PREVIEW,
      )

    val result = service.classifyBatch(companies)

    expect(result.keys).toHaveSize(2)
    expect(result[1L]!!.sector).toEqual(IndustrySector.SEMICONDUCTORS)
    expect(result[3L]!!.sector).toEqual(IndustrySector.FINANCE)
  }

  @Test
  fun `should return empty map when all fallback models fail`() {
    every { properties.enabled } returns true
    val companies =
      listOf(CompanyClassificationInput(holdingId = 1L, name = "Apple", ticker = "AAPL"))
    every { openRouterClient.classifyWithCascadingFallback(any(), AiModel.GEMINI_3_FLASH_PREVIEW) } returns null

    val result = service.classifyBatch(companies)

    expect(result.keys).toHaveSize(0)
  }

  @Test
  fun `should strip trailing commentary after sector name`() {
    every { properties.enabled } returns true
    val companies =
      listOf(
        CompanyClassificationInput(holdingId = 1L, name = "Banco Santander", ticker = "SAN"),
        CompanyClassificationInput(holdingId = 2L, name = "Apple", ticker = "AAPL"),
      )
    every { openRouterClient.classifyWithCascadingFallback(any(), AiModel.GEMINI_3_FLASH_PREVIEW) } returns
      OpenRouterClassificationResult(
        content = "1. Finance (large cap banks)\n2. Semiconductors - chip design",
        model = AiModel.GEMINI_3_FLASH_PREVIEW,
      )

    val result = service.classifyBatch(companies)

    expect(result[1L]!!.sector).toEqual(IndustrySector.FINANCE)
    expect(result[2L]!!.sector).toEqual(IndustrySector.SEMICONDUCTORS)
  }

  @Test
  fun `should skip line with unknown sector name`() {
    every { properties.enabled } returns true
    val companies =
      listOf(
        CompanyClassificationInput(holdingId = 1L, name = "Valid Corp", ticker = "VC"),
        CompanyClassificationInput(holdingId = 2L, name = "Mystery Corp", ticker = "MC"),
      )
    every { openRouterClient.classifyWithCascadingFallback(any(), AiModel.GEMINI_3_FLASH_PREVIEW) } returns
      OpenRouterClassificationResult(
        content = "1. Finance\n2. Quantum Computing",
        model = AiModel.GEMINI_3_FLASH_PREVIEW,
      )

    val result = service.classifyBatch(companies)

    expect(result.keys).toHaveSize(1)
    expect(result[1L]!!.sector).toEqual(IndustrySector.FINANCE)
    expect(result[2L]).toEqual(null)
  }

  @Test
  fun `should return empty map when classification disabled via properties`() {
    every { properties.enabled } returns false
    val companies =
      listOf(CompanyClassificationInput(holdingId = 1L, name = "Apple", ticker = "AAPL"))

    val result = service.classifyBatch(companies)

    expect(result.keys).toHaveSize(0)
    verify(exactly = 0) { openRouterClient.classifyWithCascadingFallback(any(), any()) }
  }

  @Test
  fun `should return null when last model in chain fails with unknown sector`() {
    every { properties.enabled } returns true
    every { openRouterClient.classifyWithModel(any()) } returns
      OpenRouterClassificationResult(content = "Unknown", model = AiModel.CLAUDE_OPUS_4_6)

    val result = service.classifyCompanyWithModel("Test Corp")

    expect(result).toEqual(null)
    verify(exactly = 1) { openRouterClient.classifyWithModel(any()) }
    verify(exactly = 0) { openRouterClient.classifyWithCascadingFallback(any(), any(), any(), any()) }
  }
}
