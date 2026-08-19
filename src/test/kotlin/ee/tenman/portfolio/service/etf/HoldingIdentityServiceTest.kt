package ee.tenman.portfolio.service.etf

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.HoldingIdentityCacheTestConfiguration
import ee.tenman.portfolio.configuration.IndustryClassificationProperties
import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.HOLDING_IDENTITY_CACHE
import ee.tenman.portfolio.domain.AiModel
import ee.tenman.portfolio.openrouter.OpenRouterClassificationResult
import ee.tenman.portfolio.openrouter.OpenRouterClient
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.annotation.Resource
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.cache.CacheManager
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension

class HoldingIdentityServiceTest {
  @Test
  fun `should confirm same company when model answers yes`() {
    val openRouterClient = mockk<OpenRouterClient>()
    every { openRouterClient.classifyWithCascadingFallback(any(), any(), any(), any()) } returns
      OpenRouterClassificationResult(content = "YES", model = AiModel.GEMINI_3_5_FLASH_LITE)
    val service = HoldingIdentityService(openRouterClient, IndustryClassificationProperties(enabled = true))

    val result = service.isSameCompany("NVIDIA", "NVIDIA CORP", "NVDA")

    expect(result).toEqual(true)
  }

  @Test
  fun `should reject different companies when model answers no`() {
    val openRouterClient = mockk<OpenRouterClient>()
    every { openRouterClient.classifyWithCascadingFallback(any(), any(), any(), any()) } returns
      OpenRouterClassificationResult(content = "NO", model = AiModel.GEMINI_3_5_FLASH_LITE)
    val service = HoldingIdentityService(openRouterClient, IndustryClassificationProperties(enabled = true))

    val result = service.isSameCompany("Merck & Co.", "Merck KGaA", "MRK")

    expect(result).toEqual(false)
  }

  @Test
  fun `should treat affirmative answer with surrounding whitespace and lowercase as yes`() {
    val openRouterClient = mockk<OpenRouterClient>()
    every { openRouterClient.classifyWithCascadingFallback(any(), any(), any(), any()) } returns
      OpenRouterClassificationResult(content = "  yes, identical entity\n", model = AiModel.GEMINI_3_5_FLASH_LITE)
    val service = HoldingIdentityService(openRouterClient, IndustryClassificationProperties(enabled = true))

    val result = service.isSameCompany("Amazon", "Amazon.com Inc", "AMZN")

    expect(result).toEqual(true)
  }

  @Test
  fun `should return no verdict when answer is not a clear yes or no`() {
    val openRouterClient = mockk<OpenRouterClient>()
    every { openRouterClient.classifyWithCascadingFallback(any(), any(), any(), any()) } returns
      OpenRouterClassificationResult(content = "Well, they might be the same entity", model = AiModel.GEMINI_3_5_FLASH_LITE)
    val service = HoldingIdentityService(openRouterClient, IndustryClassificationProperties(enabled = true))

    val result = service.isSameCompany("ASML Holding", "ASML Hōldings NV", "ASML")

    expect(result).toEqual(null)
  }

  @Test
  fun `should return no verdict when model returns no response`() {
    val openRouterClient = mockk<OpenRouterClient>()
    every { openRouterClient.classifyWithCascadingFallback(any(), any(), any(), any()) } returns null
    val service = HoldingIdentityService(openRouterClient, IndustryClassificationProperties(enabled = true))

    val result = service.isSameCompany("Micron", "Micron Technology Inc", "MU")

    expect(result).toEqual(null)
  }

  @Test
  fun `should return no verdict without consulting model when classification is disabled`() {
    val openRouterClient = mockk<OpenRouterClient>()
    val service = HoldingIdentityService(openRouterClient, IndustryClassificationProperties(enabled = false))

    val result = service.isSameCompany("Alphabet", "Alphabet Inc", "GOOGL")

    expect(result).toEqual(null)
  }

  @Test
  fun `should confirm identity without consulting model when names match case insensitively`() {
    val openRouterClient = mockk<OpenRouterClient>()
    val service = HoldingIdentityService(openRouterClient, IndustryClassificationProperties(enabled = true))

    val result = service.isSameCompany("Évolution SA", "évolution sa", null)

    expect(result).toEqual(true)
  }

  @Test
  fun `should return no verdict without consulting model when existing name is blank`() {
    val openRouterClient = mockk<OpenRouterClient>()
    val service = HoldingIdentityService(openRouterClient, IndustryClassificationProperties(enabled = true))

    val result = service.isSameCompany("   ", "Apple Inc", "AAPL")

    expect(result).toEqual(null)
  }
}

@ExtendWith(SpringExtension::class)
@ContextConfiguration(classes = [HoldingIdentityCacheTestConfiguration::class])
@ActiveProfiles("holding-identity-cache-test")
class HoldingIdentityServiceCacheTest {
  @Resource
  private lateinit var holdingIdentityService: HoldingIdentityService

  @Resource
  private lateinit var openRouterClient: OpenRouterClient

  @Resource
  private lateinit var testCacheManager: CacheManager

  @BeforeEach
  fun setup() {
    testCacheManager.getCache(HOLDING_IDENTITY_CACHE)?.clear()
    clearMocks(openRouterClient)
  }

  @Test
  fun `should cache negative verdict and not invoke model again`() {
    every { openRouterClient.classifyWithCascadingFallback(any(), any(), any(), any()) } returns
      OpenRouterClassificationResult(content = "NO", model = AiModel.DEEPSEEK_V4_FLASH)

    holdingIdentityService.isSameCompany("Merck & Co.", "Merck KGaA", "MRK")
    holdingIdentityService.isSameCompany("Merck & Co.", "Merck KGaA", "MRK")

    verify(exactly = 1) { openRouterClient.classifyWithCascadingFallback(any(), any(), any(), any()) }
  }

  @Test
  fun `should cache positive verdict and not invoke model again`() {
    every { openRouterClient.classifyWithCascadingFallback(any(), any(), any(), any()) } returns
      OpenRouterClassificationResult(content = "YES", model = AiModel.DEEPSEEK_V4_FLASH)

    holdingIdentityService.isSameCompany("NVIDIA", "NVIDIA CORP", "NVDA")
    holdingIdentityService.isSameCompany("NVIDIA", "NVIDIA CORP", "NVDA")

    verify(exactly = 1) { openRouterClient.classifyWithCascadingFallback(any(), any(), any(), any()) }
  }

  @Test
  fun `cannot reuse cached verdict across different name splits with same concatenation`() {
    every { openRouterClient.classifyWithCascadingFallback(any(), any(), any(), any()) } returnsMany
      listOf(
        OpenRouterClassificationResult(content = "YES", model = AiModel.DEEPSEEK_V4_FLASH),
        OpenRouterClassificationResult(content = "NO", model = AiModel.DEEPSEEK_V4_FLASH),
      )

    holdingIdentityService.isSameCompany("Apple|Inc", "Corp", null)
    val second = holdingIdentityService.isSameCompany("Apple", "Inc|Corp", null)

    expect(second).toEqual(false)
  }

  @Test
  fun `should not cache missing verdict and invoke model again`() {
    every { openRouterClient.classifyWithCascadingFallback(any(), any(), any(), any()) } returns null

    holdingIdentityService.isSameCompany("Micron", "Micron Technology Inc", "MU")
    holdingIdentityService.isSameCompany("Micron", "Micron Technology Inc", "MU")

    verify(exactly = 2) { openRouterClient.classifyWithCascadingFallback(any(), any(), any(), any()) }
  }
}
