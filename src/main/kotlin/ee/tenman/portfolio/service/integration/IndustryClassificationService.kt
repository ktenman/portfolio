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

  companion object {
    private val CRYPTO_PATTERNS = setOf("btceur", "bitcoin", "binance", "bnbeur")
  }

  fun classifyCompany(companyName: String): IndustrySector? = classifyCompanyWithModel(companyName)?.sector

  fun classifyCompanyWithModel(companyName: String): SectorClassificationResult? {
    val sanitizedName = LogSanitizerUtil.sanitize(companyName)
    log.info("Classifying company: $sanitizedName")
    if (!properties.enabled || companyName.isBlank()) {
      log.warn("Classification disabled or blank company name")
      return null
    }
    val cryptoMatch = detectCryptocurrency(companyName)
    if (cryptoMatch != null) {
      log.info("Hardcoded classification: $sanitizedName as Cryptocurrency (matched: $cryptoMatch)")
      return SectorClassificationResult(sector = IndustrySector.CRYPTOCURRENCY, model = null)
    }
    val prompt = buildPrompt(companyName)
    return classifyWithPrimaryModel(prompt, sanitizedName)
  }

  private fun detectCryptocurrency(
    companyName: String,
    ticker: String? = null,
  ): String? {
    val lowerName = companyName.lowercase()
    val lowerTicker = ticker?.lowercase()
    return CRYPTO_PATTERNS.find { pattern ->
      lowerName.contains(pattern) || lowerTicker?.contains(pattern) == true
    }
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
    val nextModel = failedModel?.nextSectorFallbackModel()
    if (failedModel != null && nextModel == null) {
      log.warn("No fallback available after ${failedModel.modelId}")
      return null
    }
    val model = nextModel ?: AiModel.CLAUDE_OPUS_4_5
    log.info("Retrying classification with cascading fallback starting from ${model.modelId} for: $sanitizedName")
    val response =
      openRouterClient.classifyWithCascadingFallback(prompt, model) ?: run {
        log.warn("All fallback models exhausted for company: $sanitizedName")
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
      if (logUnknownSector) log.warn("Unknown sector from model response: $content")
      return null
    }
    log.info("Classified $sanitizedName as ${sector.displayName} using model ${response.model}")
    return SectorClassificationResult(sector = sector, model = response.model)
  }

  fun classifyBatch(companies: List<SectorClassificationInput>): Map<Long, SectorClassificationResult> {
    if (companies.isEmpty()) return emptyMap()
    val validCompanies = companies.filter { !it.name.isBlank() }
    if (validCompanies.isEmpty()) {
      log.info("No valid companies to classify in batch")
      return emptyMap()
    }
    val cryptoAssigned = mutableMapOf<Long, SectorClassificationResult>()
    val needsLlm = mutableListOf<SectorClassificationInput>()
    validCompanies.forEach { company ->
      val cryptoMatch = detectCryptocurrency(company.name, company.ticker)
      if (cryptoMatch != null) {
        log.info("Hardcoded: ${company.name} (${company.ticker}) as Cryptocurrency (matched: $cryptoMatch)")
        cryptoAssigned[company.holdingId] = SectorClassificationResult(IndustrySector.CRYPTOCURRENCY, null)
      } else {
        needsLlm.add(company)
      }
    }
    if (needsLlm.isEmpty()) {
      log.info("All ${cryptoAssigned.size} companies hardcoded as crypto")
      return cryptoAssigned
    }
    log.info("Batch classifying ${needsLlm.size} sectors (${cryptoAssigned.size} hardcoded)")
    val llmResults = classifyBatchWithLlm(needsLlm)
    return cryptoAssigned + llmResults
  }

  private fun classifyBatchWithLlm(companies: List<SectorClassificationInput>): Map<Long, SectorClassificationResult> {
    if (!properties.enabled) {
      log.warn("LLM classification disabled")
      return emptyMap()
    }
    val prompt = buildBatchPrompt(companies)
    val response = openRouterClient.classifyWithModel(prompt)
    if (response == null) {
      log.warn("Batch sector classification failed for ${companies.size} companies")
      return emptyMap()
    }
    return parseBatchResponse(response.content, companies, response.model)
  }

  private fun buildBatchPrompt(companies: List<SectorClassificationInput>): String {
    val companiesList =
      companies
        .mapIndexed { index, company -> "${index + 1}. ${company.name}" }
        .joinToString("\n")
    val categories = IndustrySector.getAllDisplayNames()
    return """
    Classify each company into ONE category: $categories

    Companies:
    $companiesList

    Reply format (one per line, category name only):
    1. CategoryName
    2. CategoryName
    ...
    """.trimIndent()
  }

  private fun parseBatchResponse(
    content: String?,
    companies: List<SectorClassificationInput>,
    model: AiModel?,
  ): Map<Long, SectorClassificationResult> {
    if (content.isNullOrBlank()) return emptyMap()
    val results = mutableMapOf<Long, SectorClassificationResult>()
    val linePattern = Regex("(\\d+)\\.?\\s*(.+)")
    content.lines().forEach { line ->
      val match = linePattern.find(line.trim()) ?: return@forEach
      val index = match.groupValues[1].toIntOrNull()?.minus(1) ?: return@forEach
      val sectorName = match.groupValues[2].trim()
      if (index !in companies.indices) return@forEach
      val sector = IndustrySector.fromDisplayName(sectorName) ?: return@forEach
      val company = companies[index]
      results[company.holdingId] = SectorClassificationResult(sector = sector, model = model)
      log.info("Batch classified '${company.name}' as ${sector.displayName}")
    }
    if (results.size < companies.size / 2) {
      log.warn("Low parse success rate for sector batch: ${results.size}/${companies.size}")
    }
    log.info("Successfully parsed ${results.size}/${companies.size} batch sector results")
    return results
  }

  private fun buildPrompt(companyName: String): String =
    """
    Classify $companyName into ONE category: ${IndustrySector.getAllDisplayNames()}

    ANSWER WITH ONLY THE CATEGORY NAME. DO NOT EXPLAIN YOUR REASONING.

    Category:
    """.trimIndent()
}
