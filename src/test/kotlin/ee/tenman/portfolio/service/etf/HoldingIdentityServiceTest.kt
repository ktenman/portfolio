package ee.tenman.portfolio.service.etf

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.IndustryClassificationProperties
import ee.tenman.portfolio.domain.AiModel
import ee.tenman.portfolio.openrouter.OpenRouterClassificationResult
import ee.tenman.portfolio.openrouter.OpenRouterClient
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test

class HoldingIdentityServiceTest {
  @Test
  fun `should confirm same company when model answers yes`() {
    val openRouterClient = mockk<OpenRouterClient>()
    every { openRouterClient.classifyWithCascadingFallback(any(), any(), any(), any()) } returns
      OpenRouterClassificationResult(content = "YES", model = AiModel.GEMINI_3_FLASH_PREVIEW)
    val service = HoldingIdentityService(openRouterClient, IndustryClassificationProperties(enabled = true))

    val result = service.isSameCompany("NVIDIA", "NVIDIA CORP", "NVDA")

    expect(result).toEqual(true)
  }

  @Test
  fun `should reject different companies when model answers no`() {
    val openRouterClient = mockk<OpenRouterClient>()
    every { openRouterClient.classifyWithCascadingFallback(any(), any(), any(), any()) } returns
      OpenRouterClassificationResult(content = "NO", model = AiModel.GEMINI_3_FLASH_PREVIEW)
    val service = HoldingIdentityService(openRouterClient, IndustryClassificationProperties(enabled = true))

    val result = service.isSameCompany("Merck & Co.", "Merck KGaA", "MRK")

    expect(result).toEqual(false)
  }

  @Test
  fun `should treat affirmative answer with surrounding whitespace and lowercase as yes`() {
    val openRouterClient = mockk<OpenRouterClient>()
    every { openRouterClient.classifyWithCascadingFallback(any(), any(), any(), any()) } returns
      OpenRouterClassificationResult(content = "  yes, identical entity\n", model = AiModel.GEMINI_3_FLASH_PREVIEW)
    val service = HoldingIdentityService(openRouterClient, IndustryClassificationProperties(enabled = true))

    val result = service.isSameCompany("Amazon", "Amazon.com Inc", "AMZN")

    expect(result).toEqual(true)
  }

  @Test
  fun `should reject identity when model returns no response`() {
    val openRouterClient = mockk<OpenRouterClient>()
    every { openRouterClient.classifyWithCascadingFallback(any(), any(), any(), any()) } returns null
    val service = HoldingIdentityService(openRouterClient, IndustryClassificationProperties(enabled = true))

    val result = service.isSameCompany("Micron", "Micron Technology Inc", "MU")

    expect(result).toEqual(false)
  }

  @Test
  fun `should reject identity without consulting model when classification is disabled`() {
    val openRouterClient = mockk<OpenRouterClient>()
    val service = HoldingIdentityService(openRouterClient, IndustryClassificationProperties(enabled = false))

    val result = service.isSameCompany("Alphabet", "Alphabet Inc", "GOOGL")

    expect(result).toEqual(false)
  }

  @Test
  fun `should confirm identity without consulting model when names match case insensitively`() {
    val openRouterClient = mockk<OpenRouterClient>()
    val service = HoldingIdentityService(openRouterClient, IndustryClassificationProperties(enabled = true))

    val result = service.isSameCompany("Évolution SA", "évolution sa", null)

    expect(result).toEqual(true)
  }

  @Test
  fun `should reject identity without consulting model when existing name is blank`() {
    val openRouterClient = mockk<OpenRouterClient>()
    val service = HoldingIdentityService(openRouterClient, IndustryClassificationProperties(enabled = true))

    val result = service.isSameCompany("   ", "Apple Inc", "AAPL")

    expect(result).toEqual(false)
  }
}
