package ee.tenman.portfolio.openrouter

import ee.tenman.portfolio.domain.AiModel
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class OpenRouterClient(
  private val openRouterFeignClient: OpenRouterFeignClient,
  private val openRouterProperties: OpenRouterProperties,
  private val circuitBreaker: OpenRouterCircuitBreaker,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  fun classify(
    prompt: String,
    maxTokens: Int = 500,
    temperature: Double = 0.1,
  ): String? = classifyWithModel(prompt, maxTokens, temperature)?.content

  fun classifyWithModel(
    prompt: String,
    maxTokens: Int = 500,
    temperature: Double = 0.1,
  ): OpenRouterClassificationResult? {
    if (openRouterProperties.apiKey.isBlank()) {
      log.warn("OpenRouter API key is not configured")
      return null
    }
    val selection = circuitBreaker.selectModel()
    if (!circuitBreaker.tryAcquireRateLimit(selection.isUsingFallback)) {
      log.warn("Rate limit exceeded for {} model, skipping request", if (selection.isUsingFallback) "fallback" else "primary")
      return null
    }
    val request =
      OpenRouterRequest(
        model = selection.modelId,
        messages = listOf(OpenRouterRequest.Message(role = "user", content = prompt)),
        maxTokens = maxTokens,
        temperature = temperature,
      )
    return executeRequest(request, selection)
  }

  private fun executeRequest(
    request: OpenRouterRequest,
    selection: ModelSelection,
  ): OpenRouterClassificationResult? =
    runCatching {
      log.info("Calling OpenRouter API with model: {} (fallback: {})", selection.modelId, selection.isUsingFallback)
      val response = openRouterFeignClient.chatCompletion("Bearer ${openRouterProperties.apiKey}", request)
      val content = response.extractContent()
      log.info("OpenRouter response successful, content: '{}'", content)
      circuitBreaker.recordSuccess()
      OpenRouterClassificationResult(content = content, model = AiModel.fromModelId(selection.modelId))
    }.onFailure { e ->
      log.error("Error calling OpenRouter API with model {}: {}", selection.modelId, e.message, e)
      val exception = e as? Exception ?: RuntimeException(e)
      circuitBreaker.recordFailure(exception)
    }.getOrNull()
}
