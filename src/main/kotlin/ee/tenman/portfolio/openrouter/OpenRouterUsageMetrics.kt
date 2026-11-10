package ee.tenman.portfolio.openrouter

import ee.tenman.portfolio.domain.AiPurpose
import feign.FeignException
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import org.springframework.stereotype.Component

@Component
class OpenRouterUsageMetrics(
  private val registry: MeterRegistry,
) {
  fun measure(
    purpose: AiPurpose,
    model: String,
    call: () -> OpenRouterResponse,
  ): OpenRouterResponse {
    seed(purpose, model)
    return runCatching(call)
      .onSuccess { record(purpose, model, it) }
      .onFailure { fail(purpose, model, it) }
      .getOrThrow()
  }

  private fun seed(
    purpose: AiPurpose,
    model: String,
  ) {
    val tags = tags(purpose, model)
    registry.counter(COST, tags)
    registry.counter(CALLS, tags.and("outcome", "success"))
    listOf("prompt", "completion", "reasoning").forEach { registry.counter(TOKENS, tags.and("type", it)) }
  }

  private fun record(
    purpose: AiPurpose,
    model: String,
    response: OpenRouterResponse,
  ) {
    count(purpose, model, if (response.extractContent() == null) "empty" else "success")
    val usage = response.usage ?: return
    val reasoning = usage.completionTokensDetails?.reasoningTokens
    usage.cost?.let { registry.counter(COST, tags(purpose, model)).increment(it) }
    usage.promptTokens?.let { tokens(purpose, model, "prompt", it) }
    usage.completionTokens?.let { tokens(purpose, model, "completion", (it - (reasoning ?: 0)).coerceAtLeast(0)) }
    reasoning?.let { tokens(purpose, model, "reasoning", it) }
  }

  private fun fail(
    purpose: AiPurpose,
    model: String,
    error: Throwable,
  ) {
    count(purpose, model, (error as? FeignException)?.status()?.takeIf { it > 0 }?.toString() ?: "error")
  }

  private fun count(
    purpose: AiPurpose,
    model: String,
    outcome: String,
  ) {
    registry.counter(CALLS, tags(purpose, model).and("outcome", outcome)).increment()
  }

  private fun tokens(
    purpose: AiPurpose,
    model: String,
    type: String,
    amount: Long,
  ) {
    registry.counter(TOKENS, tags(purpose, model).and("type", type)).increment(amount.toDouble())
  }

  private fun tags(
    purpose: AiPurpose,
    model: String,
  ): Tags = Tags.of("purpose", purpose.name.lowercase(), "model", model)

  companion object {
    private const val COST = "portfolio.openrouter.cost.dollars"
    private const val TOKENS = "portfolio.openrouter.tokens"
    private const val CALLS = "portfolio.openrouter.calls"
  }
}
