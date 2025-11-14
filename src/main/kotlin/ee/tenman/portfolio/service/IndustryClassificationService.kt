package ee.tenman.portfolio.service

import ee.tenman.portfolio.configuration.IndustryClassificationProperties
import ee.tenman.portfolio.domain.IndustrySector
import ee.tenman.portfolio.openrouter.OpenRouterClient
import ee.tenman.portfolio.util.LogSanitizerUtil
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class IndustryClassificationService(
  private val openRouterClient: OpenRouterClient,
  private val properties: IndustryClassificationProperties,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  fun classifyCompany(companyName: String): IndustrySector? {
    log.info("Classifying company: {}", LogSanitizerUtil.sanitize(companyName))
    if (!properties.enabled || companyName.isBlank()) {
      log.warn("Classification disabled or blank company name")
      return null
    }

    val prompt = buildPrompt(companyName)
    val response = openRouterClient.classify(prompt)
    if (response == null) {
      log.warn("No response from OpenRouter for company: {}", LogSanitizerUtil.sanitize(companyName))
      return null
    }

    val sector = IndustrySector.fromDisplayName(response)
    log.info("Classified {} as {}", LogSanitizerUtil.sanitize(companyName), sector?.displayName ?: "UNKNOWN")
    return sector
  }

  private fun buildPrompt(companyName: String): String =
    """
    Classify $companyName into ONE category: ${IndustrySector.getAllDisplayNames()}

    ANSWER WITH ONLY THE CATEGORY NAME. DO NOT EXPLAIN YOUR REASONING.

    Category:
    """.trimIndent()
}
