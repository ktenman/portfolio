package ee.tenman.portfolio.openrouter

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.AiModel
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class OpenRouterClientCascadeTest {
  private val feignClient = mockk<OpenRouterFeignClient>()
  private val circuitBreaker = mockk<OpenRouterCircuitBreaker>(relaxed = true)
  private val properties = OpenRouterProperties(apiKey = "test-key")
  private lateinit var client: OpenRouterClient

  @BeforeEach
  fun setup() {
    client = OpenRouterClient(feignClient, properties, circuitBreaker)
    every { circuitBreaker.tryAcquireForModel(any()) } returns true
  }

  @Test
  fun `should cascade to next model when response content is blank`() {
    every { feignClient.chatCompletion(any(), any()) } returnsMany listOf(okResponse(""), okResponse("Finance"))

    val result = client.classifyWithCascadingFallback("prompt", AiModel.primarySectorModel())

    expect(result?.model).toEqual(AiModel.GEMINI_3_FLASH_PREVIEW)
  }

  @Test
  fun `cannot produce result when every model responds with blank content`() {
    every { feignClient.chatCompletion(any(), any()) } returns okResponse("")

    val result = client.classifyWithCascadingFallback("prompt", AiModel.primarySectorModel())

    expect(result).toEqual(null)
  }

  private fun okResponse(content: String) =
    OpenRouterResponse(
      choices =
        listOf(
        OpenRouterResponse.Choice(
          message = OpenRouterResponse.Message(content = content),
        ),
      ),
        )
}
