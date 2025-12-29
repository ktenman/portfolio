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
  ): OpenRouterClassificationResult? = executeWithSelection(circuitBreaker.selectModel(), prompt, maxTokens, temperature)

  fun classifyWithFallback(
    prompt: String,
    maxTokens: Int = 500,
    temperature: Double = 0.1,
  ): OpenRouterClassificationResult? = executeWithSelection(circuitBreaker.selectFallbackModel(), prompt, maxTokens, temperature)

  fun classifyWithCascadingFallback(
    prompt: String,
    startingModel: AiModel,
    maxTokens: Int = 500,
    temperature: Double = 0.1,
  ): OpenRouterClassificationResult? {
    if (openRouterProperties.apiKey.isBlank()) {
      log.warn("OpenRouter API key is not configured")
      return null
    }
    var currentModel: AiModel? = startingModel
    while (currentModel != null) {
      val selection = ModelSelection(model = currentModel, fallbackTier = currentModel.sectorFallbackTier)
      val result = executeWithSelectionForCascade(selection, prompt, maxTokens, temperature)
      if (result != null) return result
      val nextModel = currentModel.nextSectorFallbackModel()
      if (nextModel != null) {
        log.info("Cascading to next fallback model: ${nextModel.modelId} (tier ${nextModel.sectorFallbackTier})")
      }
      currentModel = nextModel
    }
    log.warn("All fallback models exhausted, no successful response")
    return null
  }

  fun classifyWithCountryFallback(
    prompt: String,
    maxTokens: Int = 10,
    temperature: Double = 0.0,
  ): OpenRouterClassificationResult? {
    if (openRouterProperties.apiKey.isBlank()) {
      log.warn("OpenRouter API key is not configured")
      return null
    }
    var currentModel: AiModel? = AiModel.primaryCountryModel()
    while (currentModel != null) {
      val selection = ModelSelection(model = currentModel, fallbackTier = currentModel.countryFallbackTier)
      val result = executeWithSelectionForCascade(selection, prompt, maxTokens, temperature)
      if (result != null) return result
      val nextModel = currentModel.nextCountryFallbackModel()
      if (nextModel != null) {
        log.info("Cascading to next country fallback model: ${nextModel.modelId} (tier ${nextModel.countryFallbackTier})")
      }
      currentModel = nextModel
    }
    log.warn("All country fallback models exhausted, no successful response")
    return null
  }

  private fun executeWithSelection(
    selection: ModelSelection,
    prompt: String,
    maxTokens: Int,
    temperature: Double,
  ): OpenRouterClassificationResult? {
    if (openRouterProperties.apiKey.isBlank()) {
      log.warn("OpenRouter API key is not configured")
      return null
    }
    if (!circuitBreaker.tryAcquireRateLimit(selection.isUsingFallback)) {
      val modelType = if (selection.isUsingFallback) "fallback" else "primary"
      log.warn("Rate limit exceeded for $modelType model, skipping request")
      return null
    }
    return executeRequest(selection, prompt, maxTokens, temperature)
  }

  private fun executeWithSelectionForCascade(
    selection: ModelSelection,
    prompt: String,
    maxTokens: Int,
    temperature: Double,
  ): OpenRouterClassificationResult? {
    if (!circuitBreaker.tryAcquireForModel(selection.model)) {
      log.warn("Rate limit exceeded for model ${selection.modelId}, trying next fallback")
      return null
    }
    return executeRequest(selection, prompt, maxTokens, temperature)
  }

  private fun executeRequest(
    selection: ModelSelection,
    prompt: String,
    maxTokens: Int,
    temperature: Double,
  ): OpenRouterClassificationResult? {
    val request =
      OpenRouterRequest(
        model = selection.modelId,
        messages = listOf(OpenRouterRequest.Message(role = "user", content = prompt)),
        maxTokens = maxTokens,
        temperature = temperature,
      )
    return runCatching {
      log.info("Calling OpenRouter API with model: ${selection.modelId} (tier: ${selection.fallbackTier})")
      val response = openRouterFeignClient.chatCompletion("Bearer ${openRouterProperties.apiKey}", request)
      val content = response.extractContent()
      log.info("OpenRouter response successful, content: '$content'")
      circuitBreaker.recordSuccess()
      OpenRouterClassificationResult(content = content, model = AiModel.fromModelId(selection.modelId))
    }.onFailure { throwable ->
      log.error("Error calling OpenRouter API with model ${selection.modelId}: ${throwable.message}", throwable)
      circuitBreaker.recordFailure(throwable)
    }.getOrNull()
  }
}
