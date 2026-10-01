package ee.tenman.portfolio.service.etf

import ch.tutteli.atrium.api.fluent.en_GB.toContain
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.HOLDING_IDENTITY_CACHE
import ee.tenman.portfolio.domain.AiModel
import ee.tenman.portfolio.dto.IdentityPair
import ee.tenman.portfolio.openrouter.OpenRouterClassificationResult
import ee.tenman.portfolio.openrouter.OpenRouterClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.cache.concurrent.ConcurrentMapCacheManager

class HoldingIdentityBatchServiceTest {
  private val openRouterClient = mockk<OpenRouterClient>()
  private val cacheService = mockk<HoldingIdentityCacheService>()
  private val cacheManager = ConcurrentMapCacheManager(HOLDING_IDENTITY_CACHE)
  private val service = HoldingIdentityBatchService(openRouterClient, cacheService, cacheManager)
  private val alibaba = IdentityPair("Alibaba", "ALIBABA GROUP HOLDING", "BABA")
  private val merck = IdentityPair("Merck & Co.", "Merck KGaA", "MRK")
  private val zalgiris = IdentityPair("Žalgiris Bankas", "Žalgiris Bankas AB", null)

  private fun answer(content: String?) {
    every { openRouterClient.classifyWithCascadingFallback(any(), any(), any(), any()) } returns
      content?.let { OpenRouterClassificationResult(content = it, model = AiModel.DEEPSEEK_V4_FLASH) }
  }

  @Test
  fun `should resolve every pair from its numbered answer line`() {
    answer("1: YES\n2: NO\n3: YES")
    expect(service.resolve(listOf(alibaba, merck, zalgiris))).toEqual(mapOf(alibaba to true, merck to false, zalgiris to true))
  }

  @Test
  fun `should read numbered lines around prose and mixed punctuation`() {
    answer("Sure, here you go:\n1. yes\n2) no\n3 - YES\nThanks")
    expect(service.resolve(listOf(alibaba, merck, zalgiris))).toEqual(mapOf(alibaba to true, merck to false, zalgiris to true))
  }

  @Test
  fun `should fall back to a single check for a pair without an answer line`() {
    answer("1: YES\n3: NO")
    every { cacheService.resolve(merck) } returns false
    service.resolve(listOf(alibaba, merck, zalgiris))
    verify(exactly = 1) { cacheService.resolve(any()) }
  }

  @Test
  fun `should fall back to a single check for a pair answered twice`() {
    answer("1: YES\n1: NO\n2: NO")
    every { cacheService.resolve(alibaba) } returns true
    expect(service.resolve(listOf(alibaba, merck))).toEqual(mapOf(alibaba to true, merck to false))
  }

  @Test
  fun `should ignore answer numbers outside the batch`() {
    answer("0: NO\n1: YES\n2: NO\n9: YES")
    expect(service.resolve(listOf(alibaba, merck))).toEqual(mapOf(alibaba to true, merck to false))
  }

  @Test
  fun `should fall back to single checks for every pair when the model gives no answer`() {
    answer(null)
    every { cacheService.resolve(any()) } returns null
    expect(service.resolve(listOf(alibaba, merck))).toEqual(mapOf(alibaba to null, merck to null))
  }

  @Test
  fun `should store each batch verdict under the shared pair key`() {
    answer("1: YES\n2: NO")
    service.resolve(listOf(alibaba, merck))
    expect(cacheManager.getCache(HOLDING_IDENTITY_CACHE)?.get(merck.cacheKey)?.get()).toEqual(false)
  }

  @Test
  fun `should read a cached verdict for a pair`() {
    cacheManager.getCache(HOLDING_IDENTITY_CACHE)?.put(alibaba.cacheKey, true)
    expect(service.cached(alibaba)).toEqual(true)
  }

  @Test
  fun `should send a lone pair through the single check`() {
    every { cacheService.resolve(alibaba) } returns true
    service.resolve(listOf(alibaba))
    verify(exactly = 0) { openRouterClient.classifyWithCascadingFallback(any(), any(), any(), any()) }
  }

  @Test
  fun `should number each pair with its names and shared ticker in the prompt`() {
    val prompt = slot<String>()
    every { openRouterClient.classifyWithCascadingFallback(capture(prompt), any(), any(), any()) } returns
      OpenRouterClassificationResult(content = "1: YES\n2: YES", model = AiModel.DEEPSEEK_V4_FLASH)
    service.resolve(listOf(alibaba, zalgiris))
    expect(prompt.captured).toContain(
      "1. Name 1: Alibaba | Name 2: ALIBABA GROUP HOLDING | Possible shared ticker: BABA",
      "2. Name 1: Žalgiris Bankas | Name 2: Žalgiris Bankas AB\n",
    )
  }

  @Test
  fun `should show both tickers instead of a shared ticker when they differ`() {
    val prompt = slot<String>()
    every { openRouterClient.classifyWithCascadingFallback(capture(prompt), any(), any(), any()) } returns
      OpenRouterClassificationResult(content = "1: NO\n2: NO", model = AiModel.DEEPSEEK_V4_FLASH)
    service.resolve(listOf(IdentityPair("PTC", "PTC Therapeutics Inc", "PTCT", null, null, "PTC"), zalgiris))
    expect(prompt.captured).toContain("1. Name 1: PTC (ticker: PTC) | Name 2: PTC Therapeutics Inc (ticker: PTCT)\n")
  }

  @Test
  fun `should keep the historical cache key format`() {
    expect(IdentityPair("Apple|Inc", "Corp", null).cacheKey).toEqual("9|Apple|Inc|4|Corp|")
  }
}
