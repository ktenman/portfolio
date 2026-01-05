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
    every { openRouterClient.classifyWithCascadingFallback(any(), AiModel.CLAUDE_OPUS_4_5, any(), any()) } returns
      OpenRouterClassificationResult(content = "Software & Cloud Services", model = AiModel.CLAUDE_OPUS_4_5)

    val result = service.classifyCompanyWithModel("Apple Inc")

    expect(result?.sector).toEqual(IndustrySector.SOFTWARE_CLOUD_SERVICES)
    expect(result?.model).toEqual(AiModel.CLAUDE_OPUS_4_5)
    verify(exactly = 1) { openRouterClient.classifyWithModel(any()) }
    verify(exactly = 1) { openRouterClient.classifyWithCascadingFallback(any(), AiModel.CLAUDE_OPUS_4_5, any(), any()) }
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
  fun `should cascade to next model when claude opus returns unknown sector`() {
    every { properties.enabled } returns true
    every { openRouterClient.classifyWithModel(any()) } returns
      OpenRouterClassificationResult(content = "Unknown Category", model = AiModel.CLAUDE_OPUS_4_5)
    every { openRouterClient.classifyWithCascadingFallback(any(), AiModel.CLAUDE_SONNET_4_5, any(), any()) } returns
      OpenRouterClassificationResult(content = "Software & Cloud Services", model = AiModel.CLAUDE_SONNET_4_5)

    val result = service.classifyCompanyWithModel("Apple Inc")

    expect(result?.sector).toEqual(IndustrySector.SOFTWARE_CLOUD_SERVICES)
    expect(result?.model).toEqual(AiModel.CLAUDE_SONNET_4_5)
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
  fun `should use GEMINI_2_5_FLASH as cascading fallback after CLAUDE_SONNET_4_5 fails`() {
    every { properties.enabled } returns true
    every { openRouterClient.classifyWithModel(any()) } returns
      OpenRouterClassificationResult(content = "Unknown", model = AiModel.CLAUDE_SONNET_4_5)
    every { openRouterClient.classifyWithCascadingFallback(any(), AiModel.GEMINI_2_5_FLASH, any(), any()) } returns
      OpenRouterClassificationResult(content = "Semiconductors", model = AiModel.GEMINI_2_5_FLASH)

    val result = service.classifyCompanyWithModel("Nvidia")

    expect(result?.sector).toEqual(IndustrySector.SEMICONDUCTORS)
    expect(result?.model).toEqual(AiModel.GEMINI_2_5_FLASH)
    verify(exactly = 1) { openRouterClient.classifyWithCascadingFallback(any(), AiModel.GEMINI_2_5_FLASH, any(), any()) }
  }

  @Test
  fun `should use DEEPSEEK_V3_2 as final cascading fallback`() {
    every { properties.enabled } returns true
    every { openRouterClient.classifyWithModel(any()) } returns
      OpenRouterClassificationResult(content = "Unknown", model = AiModel.GEMINI_2_5_FLASH)
    every { openRouterClient.classifyWithCascadingFallback(any(), AiModel.DEEPSEEK_V3_2, any(), any()) } returns
      OpenRouterClassificationResult(content = "Finance", model = AiModel.DEEPSEEK_V3_2)

    val result = service.classifyCompanyWithModel("JPMorgan")

    expect(result?.sector).toEqual(IndustrySector.FINANCE)
    expect(result?.model).toEqual(AiModel.DEEPSEEK_V3_2)
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
      OpenRouterClassificationResult(content = "Finance", model = AiModel.CLAUDE_OPUS_4_5)

    val result = service.classifyCompanyWithModel("JPMorgan Chase")

    expect(result?.sector).toEqual(IndustrySector.FINANCE)
    expect(result?.model).toEqual(AiModel.CLAUDE_OPUS_4_5)
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
  fun `should return null when last model in chain fails with unknown sector`() {
    every { properties.enabled } returns true
    every { openRouterClient.classifyWithModel(any()) } returns
      OpenRouterClassificationResult(content = "Unknown", model = AiModel.DEEPSEEK_V3_2)

    val result = service.classifyCompanyWithModel("Test Corp")

    expect(result).toEqual(null)
    verify(exactly = 1) { openRouterClient.classifyWithModel(any()) }
    verify(exactly = 0) { openRouterClient.classifyWithCascadingFallback(any(), any(), any(), any()) }
  }

  @Test
  fun `should hardcode cryptocurrency for bitcoin holdings`() {
    every { properties.enabled } returns true

    val result = service.classifyCompanyWithModel("Bitcoin")

    expect(result?.sector).toEqual(IndustrySector.CRYPTOCURRENCY)
    expect(result?.model).toEqual(null)
    verify(exactly = 0) { openRouterClient.classifyWithModel(any()) }
  }

  @Test
  fun `should hardcode cryptocurrency for btceur holdings`() {
    every { properties.enabled } returns true

    val result = service.classifyCompanyWithModel("BTCEUR")

    expect(result?.sector).toEqual(IndustrySector.CRYPTOCURRENCY)
    expect(result?.model).toEqual(null)
    verify(exactly = 0) { openRouterClient.classifyWithModel(any()) }
  }

  @Test
  fun `should hardcode cryptocurrency for binance holdings`() {
    every { properties.enabled } returns true

    val result = service.classifyCompanyWithModel("Binance Coin")

    expect(result?.sector).toEqual(IndustrySector.CRYPTOCURRENCY)
    expect(result?.model).toEqual(null)
    verify(exactly = 0) { openRouterClient.classifyWithModel(any()) }
  }

  @Test
  fun `should hardcode cryptocurrency for bnbeur holdings`() {
    every { properties.enabled } returns true

    val result = service.classifyCompanyWithModel("BNBEUR")

    expect(result?.sector).toEqual(IndustrySector.CRYPTOCURRENCY)
    expect(result?.model).toEqual(null)
    verify(exactly = 0) { openRouterClient.classifyWithModel(any()) }
  }

  @Test
  fun `should not hardcode cryptocurrency for non crypto companies`() {
    every { properties.enabled } returns true
    every { openRouterClient.classifyWithModel(any()) } returns
      OpenRouterClassificationResult(content = "Software & Cloud Services", model = AiModel.GEMINI_3_FLASH_PREVIEW)

    val result = service.classifyCompanyWithModel("Apple Inc")

    expect(result?.sector).toEqual(IndustrySector.SOFTWARE_CLOUD_SERVICES)
    expect(result?.model).toEqual(AiModel.GEMINI_3_FLASH_PREVIEW)
    verify(exactly = 1) { openRouterClient.classifyWithModel(any()) }
  }

  @Test
  fun `should return empty map for empty batch`() {
    val result = service.classifyBatch(emptyList())
    expect(result).toEqual(emptyMap())
  }

  @Test
  fun `should hardcode crypto in batch without LLM call`() {
    val companies =
      listOf(
        SectorClassificationInput(1L, "Bitcoin"),
        SectorClassificationInput(2L, "Some Crypto", "BNBEUR"),
      )
    val result = service.classifyBatch(companies)
    expect(result.keys).toHaveSize(2)
    expect(result[1L]?.sector).toEqual(IndustrySector.CRYPTOCURRENCY)
    expect(result[2L]?.sector).toEqual(IndustrySector.CRYPTOCURRENCY)
    expect(result[1L]?.model).toEqual(null)
    verify(exactly = 0) { openRouterClient.classifyWithModel(any()) }
  }

  @Test
  fun `should detect crypto by ticker when name does not match`() {
    val companies =
      listOf(
        SectorClassificationInput(1L, "Unknown Asset", "BTCEUR"),
      )
    val result = service.classifyBatch(companies)
    expect(result.keys).toHaveSize(1)
    expect(result[1L]?.sector).toEqual(IndustrySector.CRYPTOCURRENCY)
    verify(exactly = 0) { openRouterClient.classifyWithModel(any()) }
  }

  @Test
  fun `should classify batch with LLM when not crypto`() {
    every { properties.enabled } returns true
    every { openRouterClient.classifyWithModel(any()) } returns
      OpenRouterClassificationResult("1. Semiconductors\n2. Finance", AiModel.GEMINI_3_FLASH_PREVIEW)
    val companies =
      listOf(
        SectorClassificationInput(1L, "Nvidia Corp"),
        SectorClassificationInput(2L, "JPMorgan Chase"),
      )
    val result = service.classifyBatch(companies)
    expect(result.keys).toHaveSize(2)
    expect(result[1L]?.sector).toEqual(IndustrySector.SEMICONDUCTORS)
    expect(result[2L]?.sector).toEqual(IndustrySector.FINANCE)
    expect(result[1L]?.model).toEqual(AiModel.GEMINI_3_FLASH_PREVIEW)
  }

  @Test
  fun `should combine hardcoded and LLM classified in batch`() {
    every { properties.enabled } returns true
    every { openRouterClient.classifyWithModel(any()) } returns
      OpenRouterClassificationResult("1. Health", AiModel.GEMINI_3_FLASH_PREVIEW)
    val companies =
      listOf(
        SectorClassificationInput(1L, "Bitcoin Holdings"),
        SectorClassificationInput(2L, "Pfizer Inc"),
      )
    val result = service.classifyBatch(companies)
    expect(result.keys).toHaveSize(2)
    expect(result[1L]?.sector).toEqual(IndustrySector.CRYPTOCURRENCY)
    expect(result[1L]?.model).toEqual(null)
    expect(result[2L]?.sector).toEqual(IndustrySector.HEALTH)
    expect(result[2L]?.model).toEqual(AiModel.GEMINI_3_FLASH_PREVIEW)
  }

  @Test
  fun `should use fallback when primary batch classification fails`() {
    every { properties.enabled } returns true
    every { openRouterClient.classifyWithModel(any()) } returns null
    every { openRouterClient.classifyWithCascadingFallback(any(), AiModel.CLAUDE_OPUS_4_5, any(), any()) } returns
      OpenRouterClassificationResult("1. Semiconductors\n2. Finance", AiModel.CLAUDE_OPUS_4_5)
    val companies =
      listOf(
        SectorClassificationInput(1L, "Nvidia Corp"),
        SectorClassificationInput(2L, "JPMorgan Chase"),
      )
    val result = service.classifyBatch(companies)
    expect(result.keys).toHaveSize(2)
    expect(result[1L]?.sector).toEqual(IndustrySector.SEMICONDUCTORS)
    expect(result[2L]?.sector).toEqual(IndustrySector.FINANCE)
    expect(result[1L]?.model).toEqual(AiModel.CLAUDE_OPUS_4_5)
    verify(exactly = 1) { openRouterClient.classifyWithModel(any()) }
    verify(exactly = 1) { openRouterClient.classifyWithCascadingFallback(any(), AiModel.CLAUDE_OPUS_4_5, any(), any()) }
  }

  @Test
  fun `should use fallback when primary returns unparseable response`() {
    every { properties.enabled } returns true
    every { openRouterClient.classifyWithModel(any()) } returns
      OpenRouterClassificationResult("Invalid response format", AiModel.GEMINI_3_FLASH_PREVIEW)
    every { openRouterClient.classifyWithCascadingFallback(any(), AiModel.CLAUDE_OPUS_4_5, any(), any()) } returns
      OpenRouterClassificationResult("1. Health", AiModel.CLAUDE_OPUS_4_5)
    val companies = listOf(SectorClassificationInput(1L, "Pfizer Inc"))
    val result = service.classifyBatch(companies)
    expect(result.keys).toHaveSize(1)
    expect(result[1L]?.sector).toEqual(IndustrySector.HEALTH)
    expect(result[1L]?.model).toEqual(AiModel.CLAUDE_OPUS_4_5)
  }

  @Test
  fun `should return empty when all batch models fail`() {
    every { properties.enabled } returns true
    every { openRouterClient.classifyWithModel(any()) } returns null
    every { openRouterClient.classifyWithCascadingFallback(any(), any(), any(), any()) } returns null
    val companies = listOf(SectorClassificationInput(1L, "Unknown Corp"))
    val result = service.classifyBatch(companies)
    expect(result).toEqual(emptyMap())
  }
}
