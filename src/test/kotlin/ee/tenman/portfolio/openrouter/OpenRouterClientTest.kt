package ee.tenman.portfolio.openrouter

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.AiModel
import ee.tenman.portfolio.domain.AiPurpose
import feign.Request
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class OpenRouterClientTest {
  private val feignClient = mockk<OpenRouterFeignClient>()
  private val circuitBreaker = mockk<OpenRouterCircuitBreaker>(relaxed = true)
  private val properties = OpenRouterProperties(apiKey = "test-key")
  private val registry = SimpleMeterRegistry()
  private lateinit var client: OpenRouterClient

  @BeforeEach
  fun setup() {
    client = OpenRouterClient(feignClient, properties, circuitBreaker, OpenRouterUsageMetrics(registry))
    every { circuitBreaker.tryAcquireForModel(any()) } returns true
    every { circuitBreaker.recordSuccess() } just runs
  }

  @Test
  fun `appends online suffix to model id in request`() {
    val captured = slot<OpenRouterRequest>()
    every { feignClient.chatCompletion(any(), capture(captured)) } returns okResponse("{\"currency\":\"EUR\"}")

    client.classifyWithOnlineSearch(AiPurpose.FUND_CURRENCY, AiModel.GPT_6_LUNA, "prompt")

    expect(captured.captured.model).toEqual("openai/gpt-6-luna:online")
  }

  @Test
  fun `returns content from response`() {
    every { feignClient.chatCompletion(any(), any()) } returns okResponse("{\"currency\":\"USD\"}")

    val result = client.classifyWithOnlineSearch(AiPurpose.FUND_CURRENCY, AiModel.GPT_6_LUNA, "prompt")

    expect(result).toEqual("{\"currency\":\"USD\"}")
  }

  @Test
  fun `waits ten minutes for a web search answer`() {
    val options = slot<Request.Options>()
    every { feignClient.chatCompletion(any(), any(), capture(options)) } returns okResponse("[]")

    client.search(AiPurpose.SPLIT_CHECK, AiModel.GEMINI_4_ARGON, "Kas BNKE jaguneb?")

    expect(options.captured.readTimeoutMillis()).toEqual(600000)
  }

  @Test
  fun `returns null when api key blank`() {
    val blankKeyClient = OpenRouterClient(feignClient, OpenRouterProperties(apiKey = ""), circuitBreaker, OpenRouterUsageMetrics(registry))

    val result = blankKeyClient.classifyWithOnlineSearch(AiPurpose.FUND_CURRENCY, AiModel.GPT_6_LUNA, "prompt")

    expect(result).toEqual(null)
    verify(exactly = 0) { feignClient.chatCompletion(any(), any()) }
  }

  @Test
  fun `returns null when rate limit denied`() {
    every { circuitBreaker.tryAcquireForModel(any()) } returns false

    val result = client.classifyWithOnlineSearch(AiPurpose.FUND_CURRENCY, AiModel.GPT_6_LUNA, "prompt")

    expect(result).toEqual(null)
    verify(exactly = 0) { feignClient.chatCompletion(any(), any()) }
  }

  @Test
  fun `should cascade to next model when response content is blank`() {
    every { feignClient.chatCompletion(any(), any()) } returnsMany listOf(okResponse(""), okResponse("Finance"))

    val result = client.classifyWithCascadingFallback(AiPurpose.SECTOR, "prompt", AiModel.primarySectorModel())

    expect(result?.model).toEqual(AiModel.primarySectorModel().nextSectorFallbackModel())
  }

  @Test
  fun `cannot produce result when every model responds with blank content`() {
    every { feignClient.chatCompletion(any(), any()) } returns okResponse("")

    val result = client.classifyWithCascadingFallback(AiPurpose.SECTOR, "prompt", AiModel.primarySectorModel())

    expect(result).toEqual(null)
  }

  @Test
  fun `should cascade to next model when response carries only reasoning text`() {
    every { feignClient.chatCompletion(any(), any()) } returnsMany
      listOf(reasoningResponse("Hmm, Äpfel AG makes hardware so maybe"), okResponse("Finance"))

    val result = client.classifyWithCascadingFallback(AiPurpose.SECTOR, "prompt", AiModel.primarySectorModel())

    expect(result?.model).toEqual(AiModel.primarySectorModel().nextSectorFallbackModel())
  }

  @Test
  fun `returns null when feign call throws`() {
    every { feignClient.chatCompletion(any(), any()) } throws RuntimeException("boom")
    every { circuitBreaker.recordFailure(any()) } just runs

    val result = client.classifyWithOnlineSearch(AiPurpose.FUND_CURRENCY, AiModel.GPT_6_LUNA, "prompt")

    expect(result).toEqual(null)
    verify { circuitBreaker.recordFailure(any()) }
  }

  @Test
  fun `records a search call under the split check purpose`() {
    every { feignClient.chatCompletion(any(), any(), any()) } returns okResponse("[]")
    client.search(AiPurpose.SPLIT_CHECK, AiModel.GEMINI_4_ARGON, "Kas BNKE jaguneb?")
    expect(calls("purpose", "split_check", "model", AiModel.GEMINI_4_ARGON.modelId, "outcome", "success")).toEqual(1.0)
  }

  @Test
  fun `records a cascading call under its purpose`() {
    every { feignClient.chatCompletion(any(), any()) } returns okResponse("Finance")
    client.classifyWithCascadingFallback(AiPurpose.HOLDING_IDENTITY, "Põhja Pank", AiModel.primarySectorModel())
    expect(calls("purpose", "holding_identity", "outcome", "success")).toEqual(1.0)
  }

  @Test
  fun `records a failed call under its purpose`() {
    every { feignClient.chatCompletion(any(), any()) } throws IllegalStateException("Krediit on otsas")
    every { circuitBreaker.recordFailure(any()) } just runs
    client.classifyWithOnlineSearch(AiPurpose.FUND_CURRENCY, AiModel.GPT_6_LUNA, "prompt")
    expect(calls("purpose", "fund_currency", "outcome", "error")).toEqual(1.0)
  }

  @Test
  fun `records nothing when the rate limit skips the call`() {
    every { circuitBreaker.tryAcquireForModel(any()) } returns false
    client.classifyWithOnlineSearch(AiPurpose.FUND_CURRENCY, AiModel.GPT_6_LUNA, "prompt")
    expect(registry.find("portfolio.openrouter.calls").counter()).toEqual(null)
  }

  private fun calls(vararg tags: String): Double? =
    registry
      .find("portfolio.openrouter.calls")
      .tags(*tags)
      .counter()
      ?.count()

  private fun okResponse(content: String) =
    OpenRouterResponse(
      choices =
        listOf(
        OpenRouterResponse.Choice(
          message = OpenRouterResponse.Message(content = content),
        ),
      ),
        )

  private fun reasoningResponse(reasoning: String) =
    OpenRouterResponse(
      choices =
        listOf(
          OpenRouterResponse.Choice(
            message = OpenRouterResponse.Message(content = null, reasoning = reasoning),
          ),
        ),
    )
}
