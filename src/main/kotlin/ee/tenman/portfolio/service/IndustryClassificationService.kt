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

  fun classifyCompany(companyName: String): IndustrySector? = classifyCompanyWithModel(companyName)?.sector

  fun classifyCompanyWithModel(companyName: String): SectorClassificationResult? {
    log.info("Classifying company: {}", LogSanitizerUtil.sanitize(companyName))
    if (!properties.enabled || companyName.isBlank()) {
      log.warn("Classification disabled or blank company name")
      return null
    }
    return classifyWithOpenRouter(companyName)
  }

  private fun classifyWithOpenRouter(companyName: String): SectorClassificationResult? {
    val response = openRouterClient.classifyWithModel(buildPrompt(companyName))
    val content = response?.content
    if (content == null) {
      log.warn("No response from OpenRouter for company: {}", LogSanitizerUtil.sanitize(companyName))
      return null
    }
    val sector = IndustrySector.fromDisplayName(content)
    if (sector == null) {
      log.warn("Unknown sector from response: {}", content)
      return null
    }
    log.info("Classified {} as {} using model {}", LogSanitizerUtil.sanitize(companyName), sector.displayName, response.model)
    return SectorClassificationResult(sector = sector, model = response.model)
  }

  private fun buildPrompt(companyName: String): String =
    """
    Classify $companyName into ONE category: ${IndustrySector.getAllDisplayNames()}

    ANSWER WITH ONLY THE CATEGORY NAME. DO NOT EXPLAIN YOUR REASONING.

    Category:
    """.trimIndent()
}
