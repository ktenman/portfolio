package ee.tenman.portfolio.openrouter

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.JsonMapperFactory
import ee.tenman.portfolio.domain.AiPurpose
import feign.FeignException
import feign.Request
import feign.Response
import feign.RetryableException
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.readValue

class OpenRouterUsageMetricsTest {
  private val model = "google/gemini-x"

  private fun parsed(json: String): OpenRouterResponse = JsonMapperFactory.instance.readValue(json)

  private fun recorded(json: String): SimpleMeterRegistry =
    SimpleMeterRegistry().also { OpenRouterUsageMetrics(it).measure(AiPurpose.RADIO_OUTLINE, model) { parsed(json) } }

  private fun failed(error: Throwable): SimpleMeterRegistry =
    SimpleMeterRegistry().also { runCatching { OpenRouterUsageMetrics(it).measure(AiPurpose.SECTOR, model) { throw error } } }

  private fun cost(registry: SimpleMeterRegistry): Double? = registry.find("portfolio.openrouter.cost.dollars").counter()?.count()

  private fun tokens(
    registry: SimpleMeterRegistry,
    type: String,
  ): Double? =
    registry
      .find("portfolio.openrouter.tokens")
      .tags("type", type)
      .counter()
      ?.count()

  private fun calls(
    registry: SimpleMeterRegistry,
    outcome: String,
  ): Double? =
    registry
      .find("portfolio.openrouter.calls")
      .tags("outcome", outcome)
      .counter()
      ?.count()

  private fun request(): Request =
    Request.create(Request.HttpMethod.POST, "https://openrouter.ai/api/v1/chat/completions", emptyMap(), null, null, null)

  @Test
  fun `records the cost of a call under its purpose and model`() {
    val registry = recorded("""{"choices":[{"message":{"content":"Jah"}}],"usage":{"cost":0.0123}}""")
    expect(
      registry
        .get("portfolio.openrouter.cost.dollars")
        .tags("purpose", "radio_outline", "model", model)
        .counter()
        .count(),
    ).toEqual(0.0123)
  }

  @Test
  fun `splits tokens into prompt, completion without reasoning, and reasoning`() {
    val registry =
      recorded(
        """{"choices":[{"message":{"content":"Jah"}}],""" +
          """"usage":{"prompt_tokens":1000,"completion_tokens":300,"completion_tokens_details":{"reasoning_tokens":120}}}""",
      )
    expect(listOf("prompt", "completion", "reasoning").map { tokens(registry, it) }).toEqual(listOf(1000.0, 180.0, 120.0))
  }

  @Test
  fun `publishes the whole completion when reasoning is not reported`() {
    val registry = recorded("""{"choices":[{"message":{"content":"Jah"}}],"usage":{"prompt_tokens":10,"completion_tokens":300}}""")
    expect(listOf(tokens(registry, "completion"), tokens(registry, "reasoning"))).toEqual(listOf(300.0, 0.0))
  }

  @Test
  fun `counts a call that returned content as success`() {
    expect(calls(recorded("""{"choices":[{"message":{"content":"Tehnoloogia"}}]}"""), "success")).toEqual(1.0)
  }

  @Test
  fun `counts a call without content as empty`() {
    expect(calls(recorded("""{"choices":[{"message":{"content":"  "}}]}"""), "empty")).toEqual(1.0)
  }

  @Test
  fun `records no cost for a call without usage`() {
    val registry = recorded("""{"choices":[{"message":{"content":"Tehnoloogia"}}]}""")
    expect(cost(registry)).toEqual(0.0)
  }

  @Test
  fun `publishes the cost, tokens and successful calls at zero while the call is running`() {
    val registry = SimpleMeterRegistry()
    lateinit var running: List<Double?>
    OpenRouterUsageMetrics(registry).measure(AiPurpose.SPLIT_CHECK, model) {
      running =
        listOf(cost(registry), calls(registry, "success")) + listOf("prompt", "completion", "reasoning").map { tokens(registry, it) }
      parsed("""{"choices":[{"message":{"content":"Ei jagune"}}],"usage":{"cost":0.5}}""")
    }
    expect(running).toEqual(listOf(0.0, 0.0, 0.0, 0.0, 0.0))
  }

  @Test
  fun `records a vision call under the purpose it is given`() {
    val registry = SimpleMeterRegistry()
    val client =
      object : OpenRouterVisionClient {
        override fun chatCompletion(
          authorization: String,
          request: OpenRouterVisionRequest,
        ): OpenRouterResponse = parsed("""{"choices":[{"message":{"content":"123 ÄÖÜ"}}]}""")
      }
    OpenRouterVisionService(client, OpenRouterProperties(apiKey = "võti"), OpenRouterUsageMetrics(registry))
      .extractText(AiPurpose.LICENSE_PLATE, OpenRouterVisionRequest(model, emptyList()))
    expect(
      registry
        .find("portfolio.openrouter.calls")
        .tags("purpose", "license_plate", "model", model, "outcome", "success")
        .counter()
        ?.count(),
    ).toEqual(1.0)
  }

  @Test
  fun `passes the failure of a call on after counting it`() {
    val error = IllegalStateException("Ühendus katkes")
    val thrown = runCatching { OpenRouterUsageMetrics(SimpleMeterRegistry()).measure(AiPurpose.SECTOR, model) { throw error } }
    expect(thrown.exceptionOrNull()).toEqual(error)
  }

  @Test
  fun `counts a rejected call under its HTTP status`() {
    val response =
      Response
        .builder()
        .status(402)
        .reason("Payment Required")
        .request(request())
        .headers(emptyMap())
        .body("""{"error":{"message":"Insufficient credits"}}""", Charsets.UTF_8)
        .build()
    expect(calls(failed(FeignException.errorStatus("chatCompletion", response)), "402")).toEqual(1.0)
  }

  @Test
  fun `counts a failure without an HTTP status as error`() {
    val error = RetryableException(-1, "Read timed out", Request.HttpMethod.POST, null as Long?, request())
    expect(calls(failed(error), "error")).toEqual(1.0)
  }
}
