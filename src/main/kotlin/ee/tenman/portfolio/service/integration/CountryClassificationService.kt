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
    ticker: String? = null,
    etfNames: List<String> = emptyList(),
  ): CountryClassificationResult? {
    val sanitizedName = LogSanitizerUtil.sanitize(companyName)
    log.info("Classifying country for company: {} (ticker: {})", sanitizedName, ticker)
    if (!properties.enabled || companyName.isBlank()) {
      log.warn("Classification disabled or blank company name")
      return null
    }
    val prompt = buildPrompt(companyName, ticker, etfNames)
    return classifyWithCountryModels(prompt, sanitizedName)
  }

  private fun classifyWithCountryModels(
    prompt: String,
    sanitizedName: String,
  ): CountryClassificationResult? {
    val response = openRouterClient.classifyWithCountryFallback(prompt)
    if (response == null) {
      log.warn("All country classification models failed for: {}", sanitizedName)
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

  private fun getCountryName(countryCode: String): String = Locale.of("", countryCode).displayCountry

  private fun buildPrompt(
    companyName: String,
    ticker: String?,
    etfNames: List<String>,
  ): String {
    val tickerInfo = if (!ticker.isNullOrBlank()) " (ticker: $ticker)" else ""
    val etfContext = if (etfNames.isNotEmpty()) "\nThis company is held in: ${etfNames.joinToString(", ")}" else ""
    return """
    What is the headquarters country of "$companyName"$tickerInfo?

    IMPORTANT: Answer with the country where the company's OPERATIONAL headquarters is located,
    not just where it is legally incorporated for tax purposes.

    Examples:
    - Ferrari (RACE): Answer IT (Italy, Maranello) - not NL even though incorporated there
    - Shell (SHEL): Answer GB (UK, London) - moved from NL in 2022
    - Unilever (ULVR): Answer GB (UK, London) - unified structure since 2020
    - Airbus (AIR): Answer FR (France, Toulouse) - operational HQ, not NL incorporation
    - Linde (LIN): Answer IE (Ireland) - moved HQ from Germany
    $etfContext

    ANSWER WITH ONLY THE 2-LETTER ISO COUNTRY CODE. DO NOT EXPLAIN.

    Country code:
    """.trimIndent()
  }
}
