package ee.tenman.portfolio.openrouter

import ee.tenman.portfolio.domain.AiPurpose
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class OpenRouterVisionService(
  private val openRouterVisionClient: OpenRouterVisionClient,
  private val openRouterProperties: OpenRouterProperties,
  private val usageMetrics: OpenRouterUsageMetrics,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  fun extractText(
    purpose: AiPurpose,
    request: OpenRouterVisionRequest,
  ): String? {
    if (openRouterProperties.apiKey.isBlank()) {
      log.warn("OpenRouter API key is not configured")
      return null
    }
    return runCatching {
      log.info("Calling OpenRouter Vision API with model: ${request.model}")
      val response =
        usageMetrics.measure(purpose, request.model) {
          openRouterVisionClient.chatCompletion("Bearer ${openRouterProperties.apiKey}", request)
        }
      val content = response.extractContent()
      log.info("OpenRouter Vision response: '$content'")
      content
    }.onFailure { throwable ->
      log.error("Error calling OpenRouter Vision API: ${throwable.message}", throwable)
    }.getOrNull()
  }
}
