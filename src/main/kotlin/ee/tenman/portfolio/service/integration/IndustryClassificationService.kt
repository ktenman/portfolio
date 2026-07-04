package ee.tenman.portfolio.service.integration

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
    log.info("Classifying company: $sanitizedName")
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
    val response =
      openRouterClient.classifyWithModel(prompt) ?: run {
        log.warn("No response from OpenRouter for company: $sanitizedName")
        return retryWithCascadingFallback(prompt, sanitizedName)
      }
    return parseResponse(response, sanitizedName) ?: retryWithCascadingFallback(prompt, sanitizedName, response.model)
  }

  private fun retryWithCascadingFallback(
    prompt: String,
    sanitizedName: String,
    failedModel: AiModel? = null,
  ): SectorClassificationResult? {
    val model = resolveRetryModel(failedModel) ?: return null
    log.info("Retrying classification with cascading fallback starting from ${model.modelId} for: $sanitizedName")
    val response =
      openRouterClient.classifyWithCascadingFallback(prompt, model) ?: run {
        log.warn("All fallback models exhausted for company: $sanitizedName")
        return null
      }
    return parseResponse(response, sanitizedName, logUnknownSector = true)
  }

  private fun resolveRetryModel(failedModel: AiModel?): AiModel? {
    if (failedModel == null) return AiModel.primarySectorModel().nextSectorFallbackModel()
    val nextModel = failedModel.nextSectorFallbackModel()
    if (nextModel == null) log.warn("No fallback available after ${failedModel.modelId}")
    return nextModel
  }

  private fun parseResponse(
    response: OpenRouterClassificationResult,
    sanitizedName: String,
    logUnknownSector: Boolean = false,
  ): SectorClassificationResult? {
    val content = response.content ?: return null
    val sector = IndustrySector.fromDisplayName(content)
    if (sector == null) {
      if (logUnknownSector) log.warn("Unknown sector from model response: $content")
      return null
    }
    log.info("Classified $sanitizedName as ${sector.displayName} using model ${response.model}")
    return SectorClassificationResult(sector = sector, model = response.model)
  }

  fun classifyBatch(companies: List<CompanyClassificationInput>): BatchClassificationOutcome<SectorClassificationResult> {
    if (companies.isEmpty()) return BatchClassificationOutcome(emptyMap(), false)
    val validCompanies = companies.filter { it.name.isNotBlank() }
    if (validCompanies.isEmpty()) return BatchClassificationOutcome(emptyMap(), false)
    return classifyValidBatch(validCompanies, companies.size)
  }

  private fun classifyValidBatch(
    validCompanies: List<CompanyClassificationInput>,
    totalCount: Int,
  ): BatchClassificationOutcome<SectorClassificationResult> {
    if (!properties.enabled) {
      log.warn("Classification disabled, skipping batch of $totalCount")
      return BatchClassificationOutcome(emptyMap(), false)
    }
    val prompt = buildBatchPrompt(validCompanies)
    val response =
      openRouterClient.classifyWithCascadingFallback(prompt, AiModel.primarySectorModel()) ?: run {
        log.warn("Batch sector classification failed for ${validCompanies.size} companies")
        return BatchClassificationOutcome(emptyMap(), false)
      }
    val results = parseBatchResponse(response.content, validCompanies, response.model)
    return BatchClassificationOutcome(results, results.isNotEmpty())
  }

  private fun buildBatchPrompt(companies: List<CompanyClassificationInput>): String {
    val companiesList =
      companies
        .mapIndexed { index, c ->
          val tickerInfo = if (!c.ticker.isNullOrBlank()) " (${c.ticker})" else ""
          "${index + 1}. ${c.name}$tickerInfo"
        }.joinToString("\n")
    return """
      Classify each company into ONE sector from: ${IndustrySector.getAllDisplayNames()}

      Reply with ONLY the company number and sector name, one per line.

      Rules:
      - Exact sector name from the list above
      - No explanations, no extra text
      - One line per company: "1. Finance" or "2. Semiconductors"

      Companies:
      $companiesList

      Reply format (one per line):
      1. Finance
      2. Semiconductors
      ...
      """.trimIndent()
  }

  private fun parseBatchResponse(
    content: String?,
    companies: List<CompanyClassificationInput>,
    model: AiModel?,
  ): Map<Long, SectorClassificationResult> {
    if (content.isNullOrBlank()) return emptyMap()
    val linePattern = Regex("(\\d+)\\.?\\s*(.+)")
    val results = mutableMapOf<Long, SectorClassificationResult>()
    content.lines().forEach { line ->
      val match = linePattern.find(line.trim()) ?: return@forEach
      val index = match.groupValues[1].toIntOrNull()?.minus(1) ?: return@forEach
      if (index !in companies.indices) return@forEach
      val raw = match.groupValues[2].trim()
      val cleaned = raw.split(Regex("[(,:]| - "), limit = 2).first().trim()
      val sector =
        IndustrySector.fromDisplayName(cleaned) ?: run {
          log.debug("Unknown sector from batch line: $cleaned")
          return@forEach
        }
      results[companies[index].holdingId] = SectorClassificationResult(sector = sector, model = model)
    }
    if (results.size < companies.size / 2) {
      log.warn("Low parse success rate for sector batch: ${results.size}/${companies.size}")
    }
    return results
  }

  private fun buildPrompt(companyName: String): String =
    """
    Classify $companyName into ONE category: ${IndustrySector.getAllDisplayNames()}

    ANSWER WITH ONLY THE CATEGORY NAME. DO NOT EXPLAIN YOUR REASONING.

    Category:
    """.trimIndent()
}
