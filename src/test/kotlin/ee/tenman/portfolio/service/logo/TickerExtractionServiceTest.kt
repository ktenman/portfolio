package ee.tenman.portfolio.service.logo

import ch.tutteli.atrium.api.fluent.en_GB.notToEqualNull
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.AiModel
import ee.tenman.portfolio.openrouter.OpenRouterClassificationResult
import ee.tenman.portfolio.openrouter.OpenRouterClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TickerExtractionServiceTest {
  private val openRouterClient = mockk<OpenRouterClient>()
  private lateinit var service: TickerExtractionService

  @BeforeEach
  fun setUp() {
    service = TickerExtractionService(openRouterClient)
  }

  @Test
  fun `should return null when company name is blank`() {
    val result = service.extractTicker("   ")

    expect(result).toEqual(null)
    verify(exactly = 0) { openRouterClient.classifyWithCascadingFallback(any(), any(), any(), any()) }
  }

  @Test
  fun `should return null when OpenRouter returns no response`() {
    every { openRouterClient.classifyWithCascadingFallback(any(), any(), any(), any()) } returns null

    val result = service.extractTicker("Apple Inc")

    expect(result).toEqual(null)
  }

  @Test
  fun `should return null when OpenRouter response has null content`() {
    every { openRouterClient.classifyWithCascadingFallback(any(), any(), any(), any()) } returns
      OpenRouterClassificationResult(content = null, model = AiModel.CLAUDE_OPUS_4_5)

    val result = service.extractTicker("Apple Inc")

    expect(result).toEqual(null)
  }

  @Test
  fun `should return null when ticker is UNKNOWN`() {
    every { openRouterClient.classifyWithCascadingFallback(any(), any(), any(), any()) } returns
      OpenRouterClassificationResult(content = "UNKNOWN", model = AiModel.CLAUDE_OPUS_4_5)

    val result = service.extractTicker("Some Unknown Company")

    expect(result).toEqual(null)
  }

  @Test
  fun `should return null when ticker contains invalid characters`() {
    every { openRouterClient.classifyWithCascadingFallback(any(), any(), any(), any()) } returns
      OpenRouterClassificationResult(content = "AAPL-US", model = AiModel.CLAUDE_OPUS_4_5)

    val result = service.extractTicker("Apple Inc")

    expect(result).toEqual(null)
  }

  @Test
  fun `should extract valid ticker from response`() {
    every { openRouterClient.classifyWithCascadingFallback(any(), any(), any(), any()) } returns
      OpenRouterClassificationResult(content = "AAPL", model = AiModel.CLAUDE_OPUS_4_5)

    val result = service.extractTicker("Apple Inc")

    expect(result).notToEqualNull()
    expect(result?.ticker).toEqual("AAPL")
    expect(result?.model).toEqual(AiModel.CLAUDE_OPUS_4_5)
  }

  @Test
  fun `should extract ticker with numbers`() {
    every { openRouterClient.classifyWithCascadingFallback(any(), any(), any(), any()) } returns
      OpenRouterClassificationResult(content = "005930", model = AiModel.CLAUDE_OPUS_4_5)

    val result = service.extractTicker("Samsung Electronics")

    expect(result).notToEqualNull()
    expect(result?.ticker).toEqual("005930")
  }

  @Test
  fun `should uppercase ticker from response`() {
    every { openRouterClient.classifyWithCascadingFallback(any(), any(), any(), any()) } returns
      OpenRouterClassificationResult(content = "aapl", model = AiModel.CLAUDE_OPUS_4_5)

    val result = service.extractTicker("Apple Inc")

    expect(result?.ticker).toEqual("AAPL")
  }

  @Test
  fun `should trim whitespace from ticker`() {
    every { openRouterClient.classifyWithCascadingFallback(any(), any(), any(), any()) } returns
      OpenRouterClassificationResult(content = "  NVDA  ", model = AiModel.CLAUDE_OPUS_4_5)

    val result = service.extractTicker("NVIDIA Corporation")

    expect(result?.ticker).toEqual("NVDA")
  }

  @Test
  fun `should return null for NA response`() {
    every { openRouterClient.classifyWithCascadingFallback(any(), any(), any(), any()) } returns
      OpenRouterClassificationResult(content = "N/A", model = AiModel.CLAUDE_OPUS_4_5)

    val result = service.extractTicker("Unknown Corp")

    expect(result).toEqual(null)
  }

  @Test
  fun `should return null for ticker longer than 10 characters`() {
    every { openRouterClient.classifyWithCascadingFallback(any(), any(), any(), any()) } returns
      OpenRouterClassificationResult(content = "VERYLONGTICKER", model = AiModel.CLAUDE_OPUS_4_5)

    val result = service.extractTicker("Some Company")

    expect(result).toEqual(null)
  }
}
