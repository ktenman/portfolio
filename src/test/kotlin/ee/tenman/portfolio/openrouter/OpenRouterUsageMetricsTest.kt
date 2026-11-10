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
    SimpleMeterRegistry().also { OpenRouterUsageMetrics(it).record(AiPurpose.RADIO_OUTLINE, model, parsed(json)) }

  private fun failed(error: Throwable): SimpleMeterRegistry =
    SimpleMeterRegistry().also { OpenRouterUsageMetrics(it).fail(AiPurpose.SECTOR, model, error) }

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
    expect(listOf(tokens(registry, "completion"), tokens(registry, "reasoning"))).toEqual(listOf(300.0, null))
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
  fun `counts a call without usage and records no cost`() {
    val registry = recorded("""{"choices":[{"message":{"content":"Tehnoloogia"}}]}""")
    val cost = registry.find("portfolio.openrouter.cost.dollars").counter()?.count()
    expect(listOf(calls(registry, "success"), cost)).toEqual(listOf(1.0, null))
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
