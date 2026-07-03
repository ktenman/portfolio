package ee.tenman.portfolio.service.etf

import ee.tenman.portfolio.configuration.HoldingIdentityCacheTestConfiguration
import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.HOLDING_IDENTITY_CACHE
import ee.tenman.portfolio.domain.AiModel
import ee.tenman.portfolio.openrouter.OpenRouterClassificationResult
import ee.tenman.portfolio.openrouter.OpenRouterClient
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.verify
import jakarta.annotation.Resource
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.cache.CacheManager
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension

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
  fun `should not cache missing verdict and invoke model again`() {
    every { openRouterClient.classifyWithCascadingFallback(any(), any(), any(), any()) } returns null

    holdingIdentityService.isSameCompany("Micron", "Micron Technology Inc", "MU")
    holdingIdentityService.isSameCompany("Micron", "Micron Technology Inc", "MU")

    verify(exactly = 2) { openRouterClient.classifyWithCascadingFallback(any(), any(), any(), any()) }
  }
}
