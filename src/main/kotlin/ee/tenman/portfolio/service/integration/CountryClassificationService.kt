package ee.tenman.portfolio.service.integration

import ee.tenman.portfolio.configuration.IndustryClassificationProperties
import ee.tenman.portfolio.domain.AiModel
import ee.tenman.portfolio.openrouter.OpenRouterClassificationResult
import ee.tenman.portfolio.openrouter.OpenRouterClient
import ee.tenman.portfolio.util.LogSanitizerUtil
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.Locale

@Service
class CountryClassificationService(
  private val openRouterClient: OpenRouterClient,
  private val properties: IndustryClassificationProperties,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  companion object {
    private val VALID_COUNTRY_CODES: Set<String> =
      Locale.getISOCountries().toSet()

    private val COMMON_COUNTRY_CODES: String =
      listOf(
        "US",
        "CN",
        "JP",
        "GB",
        "DE",
        "FR",
        "CA",
        "AU",
        "KR",
        "TW",
        "IN",
        "BR",
        "CH",
        "NL",
        "IE",
        "SE",
        "DK",
        "ES",
        "IT",
        "FI",
        "NO",
        "BE",
        "AT",
        "SG",
        "HK",
        "IL",
        "ZA",
        "MX",
      ).joinToString(", ")
  }

  fun classifyCompanyCountryWithModel(
    companyName: String,
    etfNames: List<String> = emptyList(),
  ): CountryClassificationResult? {
    val sanitizedName = LogSanitizerUtil.sanitize(companyName)
    log.info("Classifying country for company: {}", sanitizedName)
    if (!properties.enabled || companyName.isBlank()) {
      log.warn("Classification disabled or blank company name")
      return null
    }
    val prompt = buildPrompt(companyName, etfNames)
    return classifyWithPrimaryModel(prompt, sanitizedName)
  }

  private fun classifyWithPrimaryModel(
    prompt: String,
    sanitizedName: String,
  ): CountryClassificationResult? {
    val response =
      openRouterClient.classifyWithModel(prompt) ?: run {
        log.warn("No response from OpenRouter for country classification: {}", sanitizedName)
        return retryWithCascadingFallback(prompt, sanitizedName)
      }
    return parseResponse(response, sanitizedName) ?: retryWithCascadingFallback(prompt, sanitizedName, response.model)
  }

  private fun retryWithCascadingFallback(
    prompt: String,
    sanitizedName: String,
    failedModel: AiModel? = null,
  ): CountryClassificationResult? {
    val nextModel = failedModel?.nextFallbackModel()
    if (failedModel != null && nextModel == null) {
      log.warn("No fallback available after {}", failedModel.modelId)
      return null
    }
    val model = nextModel ?: AiModel.GROK_4_1_FAST
    log.info("Retrying country classification with fallback {} for: {}", model.modelId, sanitizedName)
    val response =
      openRouterClient.classifyWithCascadingFallback(prompt, model) ?: run {
        log.warn("All fallback models exhausted for country classification: {}", sanitizedName)
        return null
      }
    return parseResponse(response, sanitizedName, logUnknownCountry = true)
  }

  private fun parseResponse(
    response: OpenRouterClassificationResult,
    sanitizedName: String,
    logUnknownCountry: Boolean = false,
  ): CountryClassificationResult? {
    val content = response.content ?: return null
    val countryCode = parseCountryCode(content)
    if (countryCode == null) {
      if (logUnknownCountry) log.warn("Unknown country from model response: {}", content)
      return null
    }
    val countryName = getCountryName(countryCode)
    log.info("Classified {} as {} ({}) using model {}", sanitizedName, countryName, countryCode, response.model)
    return CountryClassificationResult(countryCode = countryCode, countryName = countryName, model = response.model)
  }

  private fun parseCountryCode(content: String): String? {
    val trimmed = content.trim().uppercase()
    if (trimmed.length == 2 && VALID_COUNTRY_CODES.contains(trimmed)) {
      return trimmed
    }
    return findCodeByName(content)
  }

  private fun findCodeByName(name: String): String? {
    val normalized = name.trim().lowercase()
    return VALID_COUNTRY_CODES.find { code ->
      getCountryName(code).lowercase() == normalized
    }
  }

  private fun getCountryName(countryCode: String): String = Locale("", countryCode).displayCountry

  private fun buildPrompt(
    companyName: String,
    etfNames: List<String>,
  ): String {
    val etfContext =
      if (etfNames.isNotEmpty()) {
        "\nThis company is held in: ${etfNames.joinToString(", ")}"
      } else {
        ""
      }
    return """
    Classify the headquarters country of "$companyName" into ONE of these ISO 2-letter codes: $COMMON_COUNTRY_CODES (or any valid ISO 3166-1 alpha-2 code)
    $etfContext
    Consider the ETF context - if the company is in a US-focused ETF (like S&P 500), it's likely US-headquartered.

    ANSWER WITH ONLY THE 2-LETTER COUNTRY CODE. DO NOT EXPLAIN.

    Country code:
    """.trimIndent()
  }
}
