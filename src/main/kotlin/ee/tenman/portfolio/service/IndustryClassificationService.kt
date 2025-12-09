package ee.tenman.portfolio.service

import ee.tenman.portfolio.configuration.IndustryClassificationProperties
import ee.tenman.portfolio.domain.AiModel
import ee.tenman.portfolio.domain.IndustrySector
import ee.tenman.portfolio.openrouter.OpenRouterClassificationResult
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
    val sanitizedName = LogSanitizerUtil.sanitize(companyName)
    log.info("Classifying company: {}", sanitizedName)
    if (!properties.enabled || companyName.isBlank()) {
      log.warn("Classification disabled or blank company name")
      return null
    }
    val prompt = buildPrompt(companyName)
    return classifyWithPrimaryModel(prompt, sanitizedName)
  }

  private fun classifyWithPrimaryModel(
    prompt: String,
    sanitizedName: String,
  ): SectorClassificationResult? {
    val response = openRouterClient.classifyWithModel(prompt) ?: run {
      log.warn("No response from OpenRouter for company: {}", sanitizedName)
      return retryWithFallbackModel(prompt, sanitizedName)
    }
    return parseResponse(response, sanitizedName)
      ?: retryIfNotFallbackModel(response.model, prompt, sanitizedName)
  }

  private fun retryIfNotFallbackModel(
    model: AiModel?,
    prompt: String,
    sanitizedName: String,
  ): SectorClassificationResult? {
    if (model == AiModel.CLAUDE_HAIKU_4_5) return null
    return retryWithFallbackModel(prompt, sanitizedName)
  }

  private fun retryWithFallbackModel(
    prompt: String,
    sanitizedName: String,
  ): SectorClassificationResult? {
    log.info("Retrying classification with fallback model for: {}", sanitizedName)
    val response = openRouterClient.classifyWithFallback(prompt) ?: run {
      log.warn("No response from fallback model for company: {}", sanitizedName)
      return null
    }
    return parseResponse(response, sanitizedName, logUnknownSector = true)
  }

  private fun parseResponse(
    response: OpenRouterClassificationResult,
    sanitizedName: String,
    logUnknownSector: Boolean = false,
  ): SectorClassificationResult? {
    val content = response.content ?: return null
    val sector = IndustrySector.fromDisplayName(content)
    if (sector == null) {
      if (logUnknownSector) log.warn("Unknown sector from model response: {}", content)
      return null
    }
    log.info("Classified {} as {} using model {}", sanitizedName, sector.displayName, response.model)
    return SectorClassificationResult(sector = sector, model = response.model)
  }

  private fun buildPrompt(companyName: String): String =
    """
    Classify $companyName into ONE category: ${IndustrySector.getAllDisplayNames()}

    ANSWER WITH ONLY THE CATEGORY NAME. DO NOT EXPLAIN YOUR REASONING.

    Category:
    """.trimIndent()
}
