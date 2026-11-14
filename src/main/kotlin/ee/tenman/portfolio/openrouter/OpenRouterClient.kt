package ee.tenman.portfolio.openrouter

import ee.tenman.portfolio.domain.AiModel
import ee.tenman.portfolio.domain.AiPurpose
import feign.FeignException
import feign.Request
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class OpenRouterClient(
  private val openRouterFeignClient: OpenRouterFeignClient,
  private val openRouterProperties: OpenRouterProperties,
  private val circuitBreaker: OpenRouterCircuitBreaker,
  private val usageMetrics: OpenRouterUsageMetrics,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  fun classify(
    purpose: AiPurpose,
    prompt: String,
    maxTokens: Int = DEFAULT_MAX_TOKENS,
    temperature: Double = 0.1,
  ): String? = classifyWithModel(purpose, prompt, maxTokens, temperature)?.content

  fun classifyWithModel(
    purpose: AiPurpose,
    prompt: String,
    maxTokens: Int = DEFAULT_MAX_TOKENS,
    temperature: Double = 0.1,
  ): OpenRouterClassificationResult? = executeWithSelection(purpose, circuitBreaker.selectModel(), prompt, maxTokens, temperature)

  fun classifyWithFallback(
    purpose: AiPurpose,
    prompt: String,
    maxTokens: Int = DEFAULT_MAX_TOKENS,
    temperature: Double = 0.1,
  ): OpenRouterClassificationResult? = executeWithSelection(purpose, circuitBreaker.selectFallbackModel(), prompt, maxTokens, temperature)

  fun classifyWithCascadingFallback(
    purpose: AiPurpose,
    prompt: String,
    startingModel: AiModel,
    maxTokens: Int = DEFAULT_MAX_TOKENS,
    temperature: Double = 0.1,
  ): OpenRouterClassificationResult? {
    if (openRouterProperties.apiKey.isBlank()) {
      log.warn("OpenRouter API key is not configured")
      return null
    }
    var currentModel: AiModel? = startingModel
    while (currentModel != null) {
      val selection = ModelSelection(model = currentModel, fallbackTier = currentModel.sectorFallbackTier)
      val attempt = executeWithSelectionForCascade(purpose, selection, prompt, maxTokens, temperature)
      attempt.getOrNull()?.let { return it }
      if (refused(attempt.exceptionOrNull())) return null
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
    purpose: AiPurpose,
    prompt: String,
    maxTokens: Int = DEFAULT_MAX_TOKENS,
    temperature: Double = 0.0,
  ): OpenRouterClassificationResult? {
    if (openRouterProperties.apiKey.isBlank()) {
      log.warn("OpenRouter API key is not configured")
      return null
    }
    var currentModel: AiModel? = AiModel.primaryCountryModel()
    while (currentModel != null) {
      val selection = ModelSelection(model = currentModel, fallbackTier = currentModel.countryFallbackTier)
      val attempt = executeWithSelectionForCascade(purpose, selection, prompt, maxTokens, temperature)
      attempt.getOrNull()?.let { return it }
      if (refused(attempt.exceptionOrNull())) return null
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
    purpose: AiPurpose,
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
    return executeRequest(purpose, selection, prompt, maxTokens, temperature).getOrNull()
  }

  private fun refused(error: Throwable?): Boolean {
    val status = (error as? FeignException)?.status() ?: return false
    if (status !in REFUSED_STATUSES) return false
    log.warn("Stopping the cascade because OpenRouter refused the key or its credits with status $status")
    return true
  }

  private fun executeWithSelectionForCascade(
    purpose: AiPurpose,
    selection: ModelSelection,
    prompt: String,
    maxTokens: Int,
    temperature: Double,
  ): Result<OpenRouterClassificationResult?> {
    repeat(MAX_RATE_LIMIT_RETRIES) { attempt ->
      val waitTimeMs = circuitBreaker.getWaitTimeMsForModel(selection.model)
      if (waitTimeMs > 0) {
        log.debug("Waiting ${waitTimeMs}ms for rate limit on model ${selection.modelId} (attempt ${attempt + 1})")
        Thread.sleep(waitTimeMs + RATE_LIMIT_BUFFER_MS)
      }
      if (circuitBreaker.tryAcquireForModel(selection.model)) {
        return executeRequest(purpose, selection, prompt, maxTokens, temperature)
      }
      Thread.sleep(RATE_LIMIT_BUFFER_MS)
    }
    log.warn("Rate limit exceeded for model ${selection.modelId} after $MAX_RATE_LIMIT_RETRIES attempts, trying next fallback")
    return Result.success(null)
  }

  fun classifyWithOnlineSearch(
    purpose: AiPurpose,
    model: AiModel,
    prompt: String,
    maxTokens: Int = 1500,
    temperature: Double = 0.1,
  ): String? =
    send(
      purpose,
      model,
      OpenRouterRequest(
        model = "${model.modelId}:online",
        messages = listOf(OpenRouterRequest.Message(role = "user", content = prompt)),
        maxTokens = maxTokens,
        temperature = temperature,
      ),
    )

  fun complete(
    purpose: AiPurpose,
    model: AiModel,
    system: String,
    user: String,
    schema: Map<String, Any>,
  ): String? =
    send(
      purpose,
      model,
      OpenRouterRequest(
        model = model.modelId,
        messages = listOf(OpenRouterRequest.Message("system", system), OpenRouterRequest.Message("user", user)),
        maxTokens = DEFAULT_MAX_TOKENS,
        temperature = 0.0.takeIf { model.takesTemperature },
        responseFormat = OpenRouterRequest.ResponseFormat(OpenRouterRequest.JsonSchema("outline", schema)),
        provider = OpenRouterRequest.ProviderPreferences(listOfNotNull(model.flexTier)),
      ),
    )

  fun search(
    purpose: AiPurpose,
    model: AiModel,
    prompt: String,
  ): String? =
    send(
      purpose,
      model,
      OpenRouterRequest(
        model = model.modelId,
        messages = listOf(OpenRouterRequest.Message("user", prompt)),
        maxTokens = SEARCH_MAX_TOKENS,
        temperature = 0.0,
        provider = OpenRouterRequest.ProviderPreferences(listOf(VERTEX), allowFallbacks = false),
        plugins = listOf(OpenRouterRequest.Plugin(id = "web", engine = "native")),
      ),
      SEARCH_OPTIONS,
    )

  private fun send(
    purpose: AiPurpose,
    model: AiModel,
    request: OpenRouterRequest,
    options: Request.Options? = null,
  ): String? {
    if (openRouterProperties.apiKey.isBlank()) {
      log.warn("OpenRouter API key is not configured")
      return null
    }
    if (!circuitBreaker.tryAcquireForModel(model)) {
      log.warn("Rate limit exceeded for model ${request.model}, skipping request")
      return null
    }
    return runCatching {
      log.info("Calling OpenRouter with model: ${request.model}")
      val response =
        usageMetrics.measure(purpose, request.model) {
          openRouterFeignClient.chatCompletion("Bearer ${openRouterProperties.apiKey}", request, options)
        }
      val content = response.extractContent()
      log.debug("OpenRouter response: '$content'")
      circuitBreaker.recordSuccess()
      content
    }.onFailure { throwable ->
      log.error("Error calling OpenRouter with model ${request.model}: ${throwable.message}", throwable)
      circuitBreaker.recordFailure(throwable)
    }.getOrNull()
  }

  companion object {
    private const val DEFAULT_MAX_TOKENS = 8000
    private const val SEARCH_MAX_TOKENS = 32000
    private val SEARCH_OPTIONS = Request.Options(Duration.ofSeconds(10), Duration.ofMinutes(10), true)
    private const val RATE_LIMIT_BUFFER_MS = 50L
    private const val MAX_RATE_LIMIT_RETRIES = 3
    private val REFUSED_STATUSES = setOf(401, 402)
    private const val VERTEX = "google-vertex"
  }

  private fun executeRequest(
    purpose: AiPurpose,
    selection: ModelSelection,
    prompt: String,
    maxTokens: Int,
    temperature: Double,
  ): Result<OpenRouterClassificationResult?> {
    val request =
      OpenRouterRequest(
        model = selection.modelId,
        messages = listOf(OpenRouterRequest.Message(role = "user", content = prompt)),
        maxTokens = maxTokens,
        temperature = temperature,
        reasoning = selection.model.reasoningEffort?.let(OpenRouterRequest::Reasoning),
      )
    return runCatching {
      log.info("Calling OpenRouter API with model: ${selection.modelId} (tier: ${selection.fallbackTier})")
      val response =
        usageMetrics.measure(purpose, request.model) {
          openRouterFeignClient.chatCompletion("Bearer ${openRouterProperties.apiKey}", request)
        }
      val content = response.extractContent()
      circuitBreaker.recordSuccess()
      if (content == null) {
        log.warn("OpenRouter response contained no content for model ${selection.modelId}")
        return@runCatching null
      }
      log.info("OpenRouter response successful, content: '$content'")
      OpenRouterClassificationResult(content = content, model = AiModel.fromModelId(selection.modelId))
    }.onFailure { throwable ->
      log.error("Error calling OpenRouter API with model ${selection.modelId}: ${throwable.message}", throwable)
      circuitBreaker.recordFailure(throwable)
    }
  }
}
