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
    private val CRYPTO_PATTERNS =
      listOf(
        "bitcoin", "btc", "ethereum", "eth", "bnb", "binance",
        "solana", "sol", "cardano", "ada", "ripple", "xrp",
        "dogecoin", "doge", "polkadot", "dot", "avalanche", "avax",
        "chainlink", "link", "uniswap", "uni", "litecoin", "ltc",
        "crypto", "blockchain", "defi", "nft",
      )
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

  private fun detectCryptocurrency(companyName: String): String? {
    val lowerName = companyName.lowercase()
    return CRYPTO_PATTERNS.find { pattern -> lowerName.contains(pattern) }
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

  private fun buildPrompt(companyName: String): String =
    """
    Classify $companyName into ONE category: ${IndustrySector.getAllDisplayNames()}

    ANSWER WITH ONLY THE CATEGORY NAME. DO NOT EXPLAIN YOUR REASONING.

    Category:
    """.trimIndent()
}
