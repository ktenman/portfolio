package ee.tenman.portfolio.service.etf

import ch.tutteli.atrium.api.fluent.en_GB.notToContain
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.HoldingIdentityCacheTestConfiguration
import ee.tenman.portfolio.configuration.IndustryClassificationProperties
import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.HOLDING_IDENTITY_CACHE
import ee.tenman.portfolio.domain.AiModel
import ee.tenman.portfolio.dto.IdentityPair
import ee.tenman.portfolio.openrouter.OpenRouterClassificationResult
import ee.tenman.portfolio.openrouter.OpenRouterClient
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import jakarta.annotation.Resource
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.cache.CacheManager
import org.springframework.cache.concurrent.ConcurrentMapCache
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private fun serviceFor(
  openRouterClient: OpenRouterClient,
  enabled: Boolean = true,
) = HoldingIdentityService(
  HoldingIdentityBatchService(
    openRouterClient,
    HoldingIdentityCacheService(openRouterClient),
    ConcurrentMapCacheManager(HOLDING_IDENTITY_CACHE),
  ),
  IndustryClassificationProperties(enabled = enabled),
)

class HoldingIdentityServiceTest {
  @Test
  fun `should confirm same company when model answers yes`() {
    val openRouterClient = mockk<OpenRouterClient>()
    every { openRouterClient.classifyWithCascadingFallback(any(), any(), any(), any()) } returns
      OpenRouterClassificationResult(content = "YES", model = AiModel.GEMINI_3_5_FLASH_LITE)

    val result = serviceFor(openRouterClient).resolveAll(listOf(IdentityPair("Alibaba", "ALIBABA GROUP HOLDING", "BABA"))).values.single()

    expect(result).toEqual(true)
  }

  @Test
  fun `should reject different companies when model answers no`() {
    val openRouterClient = mockk<OpenRouterClient>()
    every { openRouterClient.classifyWithCascadingFallback(any(), any(), any(), any()) } returns
      OpenRouterClassificationResult(content = "NO", model = AiModel.GEMINI_3_5_FLASH_LITE)

    val result = serviceFor(openRouterClient).resolveAll(listOf(IdentityPair("Merck & Co.", "Merck KGaA", "MRK"))).values.single()

    expect(result).toEqual(false)
  }

  @Test
  fun `should treat affirmative answer with surrounding whitespace and lowercase as yes`() {
    val openRouterClient = mockk<OpenRouterClient>()
    every { openRouterClient.classifyWithCascadingFallback(any(), any(), any(), any()) } returns
      OpenRouterClassificationResult(content = "  yes, identical entity\n", model = AiModel.GEMINI_3_5_FLASH_LITE)

    val result = serviceFor(openRouterClient).resolveAll(listOf(IdentityPair("Amazon", "Amazon.com Inc", "AMZN"))).values.single()

    expect(result).toEqual(true)
  }

  @Test
  fun `should return no verdict when answer is not a clear yes or no`() {
    val openRouterClient = mockk<OpenRouterClient>()
    every { openRouterClient.classifyWithCascadingFallback(any(), any(), any(), any()) } returns
      OpenRouterClassificationResult(content = "Well, they might be the same entity", model = AiModel.GEMINI_3_5_FLASH_LITE)

    val result = serviceFor(openRouterClient).resolveAll(listOf(IdentityPair("ASML Holding", "ASML Hōldings NV", "ASML"))).values.single()

    expect(result).toEqual(null)
  }

  @Test
  fun `should return no verdict when model returns no response`() {
    val openRouterClient = mockk<OpenRouterClient>()
    every { openRouterClient.classifyWithCascadingFallback(any(), any(), any(), any()) } returns null

    val result = serviceFor(openRouterClient).resolveAll(listOf(IdentityPair("Micron", "Micron Technology Inc", "MU"))).values.single()

    expect(result).toEqual(null)
  }

  @Test
  fun `should return no verdict without consulting model when classification is disabled`() {
    val openRouterClient = mockk<OpenRouterClient>()

    val result =
      serviceFor(
      openRouterClient,
      enabled = false,
    ).resolveAll(listOf(IdentityPair("Alphabet", "Alphabet Inc", "GOOGL"))).values.single()

    expect(result).toEqual(null)
  }

  @Test
  fun `should confirm identity without consulting model when names match case insensitively`() {
    val openRouterClient = mockk<OpenRouterClient>()

    val result = serviceFor(openRouterClient).resolveAll(listOf(IdentityPair("Évolution SA", "évolution sa", null))).values.single()

    expect(result).toEqual(true)
  }

  @Test
  fun `should confirm identity without consulting model when names differ only by legal form`() {
    val openRouterClient = mockk<OpenRouterClient>()

    val result =
      serviceFor(
      openRouterClient,
    ).resolveAll(listOf(IdentityPair("Banco Santander SA", "BANCO SANTANDER", "SAN"))).values.single()

    expect(result).toEqual(true)
  }

  @Test
  fun `should return no verdict without consulting model when existing name is blank`() {
    val openRouterClient = mockk<OpenRouterClient>()

    val result = serviceFor(openRouterClient).resolveAll(listOf(IdentityPair("   ", "Apple Inc", "AAPL"))).values.single()

    expect(result).toEqual(null)
  }

  @Test
  fun `should reject dissimilar names without consulting model`() {
    val openRouterClient = mockk<OpenRouterClient>()

    val result =
      serviceFor(
      openRouterClient,
    ).resolveAll(listOf(IdentityPair("China Merchants Bank", "China Life Insurance", null))).values.single()

    expect(result).toEqual(false)
  }

  @Test
  fun `should build prompt without source indentation when ticker is present`() {
    val openRouterClient = mockk<OpenRouterClient>()
    val prompt = slot<String>()
    every { openRouterClient.classifyWithCascadingFallback(capture(prompt), any(), any(), any()) } returns
      OpenRouterClassificationResult(content = "NO", model = AiModel.GEMINI_3_5_FLASH_LITE)

    serviceFor(
      openRouterClient,
    ).resolveAll(listOf(IdentityPair("Zhejiang Sanhua Intelligent Controls", "Zhejiang Sanhua Intelligen-h", "002050")))

    expect(prompt.captured).notToContain("\n ")
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
  private lateinit var holdingIdentityCacheService: HoldingIdentityCacheService

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

    holdingIdentityService.resolveAll(listOf(IdentityPair("Merck & Co.", "Merck KGaA", "MRK")))
    holdingIdentityService.resolveAll(listOf(IdentityPair("Merck & Co.", "Merck KGaA", "MRK")))

    verify(exactly = 1) { openRouterClient.classifyWithCascadingFallback(any(), any(), any(), any()) }
  }

  @Test
  fun `should cache positive verdict and not invoke model again`() {
    every { openRouterClient.classifyWithCascadingFallback(any(), any(), any(), any()) } returns
      OpenRouterClassificationResult(content = "YES", model = AiModel.DEEPSEEK_V4_FLASH)

    holdingIdentityService.resolveAll(listOf(IdentityPair("Alibaba", "ALIBABA GROUP HOLDING", "BABA")))
    holdingIdentityService.resolveAll(listOf(IdentityPair("Alibaba", "ALIBABA GROUP HOLDING", "BABA")))

    verify(exactly = 1) { openRouterClient.classifyWithCascadingFallback(any(), any(), any(), any()) }
  }

  @Test
  fun `cannot reuse cached verdict across different name splits with same concatenation`() {
    every { openRouterClient.classifyWithCascadingFallback(any(), any(), any(), any()) } returnsMany
      listOf(
        OpenRouterClassificationResult(content = "YES", model = AiModel.DEEPSEEK_V4_FLASH),
        OpenRouterClassificationResult(content = "NO", model = AiModel.DEEPSEEK_V4_FLASH),
      )

    holdingIdentityService.resolveAll(listOf(IdentityPair("Apple|Inc", "Corp", null)))
    val second = holdingIdentityService.resolveAll(listOf(IdentityPair("Apple", "Inc|Corp", null))).values.single()

    expect(second).toEqual(false)
  }

  @Test
  fun `should not cache missing verdict and invoke model again`() {
    every { openRouterClient.classifyWithCascadingFallback(any(), any(), any(), any()) } returns null

    holdingIdentityService.resolveAll(listOf(IdentityPair("Micron", "Micron Technology Inc", "MU")))
    holdingIdentityService.resolveAll(listOf(IdentityPair("Micron", "Micron Technology Inc", "MU")))

    verify(exactly = 2) { openRouterClient.classifyWithCascadingFallback(any(), any(), any(), any()) }
  }

  @Test
  fun `should not store a cache entry when the similarity gate rejects the names`() {
    holdingIdentityService.resolveAll(listOf(IdentityPair("China Merchants Bank", "China Life Insurance", null)))

    expect((testCacheManager.getCache(HOLDING_IDENTITY_CACHE) as ConcurrentMapCache).nativeCache.size).toEqual(0)
  }

  @Test
  fun `should reuse a batch verdict in a later single check`() {
    every { openRouterClient.classifyWithCascadingFallback(any(), any(), any(), any()) } returns
      OpenRouterClassificationResult(content = "1: YES\n2: NO", model = AiModel.DEEPSEEK_V4_FLASH)
    holdingIdentityService.resolveAll(
      listOf(IdentityPair("Alibaba", "ALIBABA GROUP HOLDING", "BABA"), IdentityPair("Merck & Co.", "Merck KGaA", "MRK")),
    )
    holdingIdentityService.resolveAll(listOf(IdentityPair("Merck & Co.", "Merck KGaA", "MRK")))
    verify(exactly = 1) { openRouterClient.classifyWithCascadingFallback(any(), any(), any(), any()) }
  }

  @Test
  fun `should serve a batch verdict from the cacheable single check`() {
    every { openRouterClient.classifyWithCascadingFallback(any(), any(), any(), any()) } returns
      OpenRouterClassificationResult(content = "1: YES\n2: NO", model = AiModel.DEEPSEEK_V4_FLASH)
    holdingIdentityService.resolveAll(
      listOf(IdentityPair("Alibaba", "ALIBABA GROUP HOLDING", "BABA"), IdentityPair("Merck & Co.", "Merck KGaA", "MRK")),
    )
    val verdict = holdingIdentityCacheService.resolve(IdentityPair("Merck & Co.", "Merck KGaA", "MRK"))
    verify(exactly = 1) { openRouterClient.classifyWithCascadingFallback(any(), any(), any(), any()) }
    expect(verdict).toEqual(false)
  }
}

class HoldingIdentityServiceBatchTest {
  private val batchService = mockk<HoldingIdentityBatchService>()
  private val service = HoldingIdentityService(batchService, IndustryClassificationProperties(enabled = true))
  private val misses = (1..41).map { IdentityPair("Õunake", "Õunake Grupp $it", null) }

  @Test
  fun `should decide pairs the name rules settle without the batch service`() {
    val equal = IdentityPair("Évolution SA", "évolution sa", null)
    val legalForm = IdentityPair("Banco Santander SA", "BANCO SANTANDER", "SAN")
    val dissimilar = IdentityPair("China Merchants Bank", "China Life Insurance", null)
    val blank = IdentityPair("   ", "Apple Inc", "AAPL")
    val answers = service.resolveAll(listOf(equal, legalForm, dissimilar, blank))
    expect(answers).toEqual(mapOf(equal to true, legalForm to true, dissimilar to false, blank to null))
  }

  @Test
  fun `should answer nothing without the batch service when classification is disabled`() {
    val disabled = HoldingIdentityService(batchService, IndustryClassificationProperties(enabled = false))
    expect(disabled.resolveAll(misses.take(2))).toEqual(misses.take(2).associateWith { null })
  }

  @Test
  fun `should use a cached verdict without asking the model`() {
    every { batchService.cached(any()) } returns true
    service.resolveAll(misses.take(2))
    verify(exactly = 0) { batchService.resolve(any()) }
  }

  @Test
  fun `should send uncached pairs in batches of forty`() {
    val sizes = Collections.synchronizedList(mutableListOf<Int>())
    every { batchService.cached(any()) } returns null
    every { batchService.resolve(any()) } answers { firstArg<List<IdentityPair>>().also { sizes += it.size }.associateWith { false } }
    service.resolveAll(misses)
    expect(sizes.sorted()).toEqual(listOf(1, 40))
  }

  @Test
  fun `should ask about a repeated pair once`() {
    val batch = slot<List<IdentityPair>>()
    every { batchService.cached(any()) } returns null
    every { batchService.resolve(capture(batch)) } answers { batch.captured.associateWith { true } }
    service.resolveAll(listOf(misses[0], misses[1], misses[0]))
    expect(batch.captured).toEqual(listOf(misses[0], misses[1]))
  }

  @Test
  fun `should run batches concurrently`() {
    val latch = CountDownLatch(2)
    every { batchService.cached(any()) } returns null
    every { batchService.resolve(any()) } answers {
      latch.countDown()
      firstArg<List<IdentityPair>>().associateWith { latch.await(5, TimeUnit.SECONDS) }
    }
    expect(service.resolveAll(misses).values.toSet()).toEqual(setOf<Boolean?>(true))
  }
}
