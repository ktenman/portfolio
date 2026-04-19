package ee.tenman.portfolio.openrouter

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.AiModel
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class OpenRouterClientOnlineTest {
  private val feignClient = mockk<OpenRouterFeignClient>()
  private val circuitBreaker = mockk<OpenRouterCircuitBreaker>(relaxed = true)
  private val properties = OpenRouterProperties(apiKey = "test-key")
  private lateinit var client: OpenRouterClient

  @BeforeEach
  fun setup() {
    client = OpenRouterClient(feignClient, properties, circuitBreaker)
    every { circuitBreaker.tryAcquireForModel(any()) } returns true
    every { circuitBreaker.recordSuccess() } just runs
  }

  @Test
  fun `appends online suffix to model id in request`() {
    val captured = slot<OpenRouterRequest>()
    every { feignClient.chatCompletion(any(), capture(captured)) } returns okResponse("{\"currency\":\"EUR\"}")

    client.classifyWithOnlineSearch(AiModel.GPT_5_4_NANO, "prompt")

    expect(captured.captured.model).toEqual("openai/gpt-5.4-nano:online")
  }

  @Test
  fun `returns content from response`() {
    every { feignClient.chatCompletion(any(), any()) } returns okResponse("{\"currency\":\"USD\"}")

    val result = client.classifyWithOnlineSearch(AiModel.GPT_5_4_NANO, "prompt")

    expect(result).toEqual("{\"currency\":\"USD\"}")
  }

  @Test
  fun `returns null when api key blank`() {
    val blankKeyClient = OpenRouterClient(feignClient, OpenRouterProperties(apiKey = ""), circuitBreaker)

    val result = blankKeyClient.classifyWithOnlineSearch(AiModel.GPT_5_4_NANO, "prompt")

    expect(result).toEqual(null)
    verify(exactly = 0) { feignClient.chatCompletion(any(), any()) }
  }

  @Test
  fun `returns null when rate limit denied`() {
    every { circuitBreaker.tryAcquireForModel(any()) } returns false

    val result = client.classifyWithOnlineSearch(AiModel.GPT_5_4_NANO, "prompt")

    expect(result).toEqual(null)
    verify(exactly = 0) { feignClient.chatCompletion(any(), any()) }
  }

  @Test
  fun `returns null when feign call throws`() {
    every { feignClient.chatCompletion(any(), any()) } throws RuntimeException("boom")
    every { circuitBreaker.recordFailure(any()) } just runs

    val result = client.classifyWithOnlineSearch(AiModel.GPT_5_4_NANO, "prompt")

    expect(result).toEqual(null)
    verify { circuitBreaker.recordFailure(any()) }
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
