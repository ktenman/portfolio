package ee.tenman.portfolio.service.integration

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
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
}
