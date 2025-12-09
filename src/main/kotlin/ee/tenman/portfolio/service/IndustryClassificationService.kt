package ee.tenman.portfolio.service

import ee.tenman.portfolio.configuration.IndustryClassificationProperties
import ee.tenman.portfolio.domain.AiModel
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
    val prompt = buildPrompt(companyName)
    val response = openRouterClient.classifyWithModel(prompt)
    val content =
      response?.content ?: run {
      log.warn("No response from OpenRouter for company: {}", LogSanitizerUtil.sanitize(companyName))
      return null
    }
    val sector = IndustrySector.fromDisplayName(content)
    if (sector != null) {
      log.info("Classified {} as {} using model {}", LogSanitizerUtil.sanitize(companyName), sector.displayName, response.model)
      return SectorClassificationResult(sector = sector, model = response.model)
    }
    return handleUnknownSector(content, response.model, prompt, companyName)
  }

  private fun handleUnknownSector(
    content: String,
    model: AiModel?,
    prompt: String,
    companyName: String,
  ): SectorClassificationResult? {
    if (model == AiModel.CLAUDE_HAIKU_4_5) {
      log.warn("Unknown sector from fallback model response: {}", content)
      return null
    }
    log.info("Unknown sector '{}' from primary model, retrying with fallback for: {}", content, LogSanitizerUtil.sanitize(companyName))
    return retryWithFallback(prompt, companyName)
  }

  private fun retryWithFallback(
    prompt: String,
    companyName: String,
  ): SectorClassificationResult? {
    val fallbackResponse = openRouterClient.classifyWithFallback(prompt)
    val fallbackContent = fallbackResponse?.content
    if (fallbackContent == null) {
      log.warn("No response from fallback model for company: {}", LogSanitizerUtil.sanitize(companyName))
      return null
    }
    val sector = IndustrySector.fromDisplayName(fallbackContent)
    if (sector == null) {
      log.warn("Unknown sector from fallback model response: {}", fallbackContent)
      return null
    }
    log.info(
      "Classified {} as {} using fallback model {}",
      LogSanitizerUtil.sanitize(companyName),
      sector.displayName,
      fallbackResponse.model,
    )
    return SectorClassificationResult(sector = sector, model = fallbackResponse.model)
  }

  private fun buildPrompt(companyName: String): String =
    """
    Classify $companyName into ONE category: ${IndustrySector.getAllDisplayNames()}

    ANSWER WITH ONLY THE CATEGORY NAME. DO NOT EXPLAIN YOUR REASONING.

    Category:
    """.trimIndent()
}
