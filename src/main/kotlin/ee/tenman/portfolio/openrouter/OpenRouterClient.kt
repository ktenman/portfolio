package ee.tenman.portfolio.openrouter

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class OpenRouterClient(
  private val openRouterFeignClient: OpenRouterFeignClient,
  private val openRouterProperties: OpenRouterProperties,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  fun classify(
    prompt: String,
    maxTokens: Int = 500,
    temperature: Double = 0.1,
  ): String? {
    if (openRouterProperties.apiKey.isBlank()) {
      log.warn("OpenRouter API key is not configured")
      return null
    }

    val request =
      OpenRouterRequest(
        model = openRouterProperties.model,
        messages =
          listOf(
            OpenRouterRequest.Message(
              role = "user",
              content = prompt,
            ),
          ),
        maxTokens = maxTokens,
        temperature = temperature,
      )

    return try {
      log.info("Calling OpenRouter API with model: {}", openRouterProperties.model)
      val response = openRouterFeignClient.chatCompletion("Bearer ${openRouterProperties.apiKey}", request)
      log.info("Raw OpenRouter response: choices={}, choices.size={}", response.choices, response.choices?.size)
      val content = response.extractContent()
      log.info("Extracted content: '{}'", content)
      content
    } catch (e: Exception) {
      log.error("Error calling OpenRouter API: {}", e.message, e)
      null
    }
  }
}
