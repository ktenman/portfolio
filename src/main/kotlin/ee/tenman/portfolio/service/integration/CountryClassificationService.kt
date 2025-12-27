package ee.tenman.portfolio.service.integration

import ee.tenman.portfolio.configuration.IndustryClassificationProperties
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
    private val VALID_COUNTRY_CODES: Set<String> = Locale.getISOCountries().toSet()
  }

  fun classifyCompanyCountryWithModel(
    companyName: String,
    ticker: String? = null,
    etfNames: List<String> = emptyList(),
  ): CountryClassificationResult? {
    val sanitizedName = LogSanitizerUtil.sanitize(companyName)
    log.info("Classifying country for company: {} (ticker: {})", sanitizedName, ticker)
    if (companyName.isBlank()) {
      log.warn("Blank company name")
      return null
    }
    return tryAutoAssign(etfNames, sanitizedName) ?: classifyWithLlm(companyName, ticker, etfNames, sanitizedName)
  }

  private fun classifyWithLlm(
    companyName: String,
    ticker: String?,
    etfNames: List<String>,
    sanitizedName: String,
  ): CountryClassificationResult? {
    if (!properties.enabled) {
      log.warn("LLM classification disabled, skipping: {}", sanitizedName)
      return null
    }
    val prompt = buildPrompt(companyName, ticker, etfNames)
    return classifyWithCountryModels(prompt, sanitizedName)
  }

  private fun tryAutoAssign(
    etfNames: List<String>,
    sanitizedName: String,
  ): CountryClassificationResult? {
    val anySp500 = etfNames.any { it.contains("S&P 500", ignoreCase = true) }
    if (anySp500) {
      log.info("Auto-assigning US for {} (in S&P 500 ETF)", sanitizedName)
      return CountryClassificationResult(countryCode = "US", countryName = "United States", model = null)
    }
    return null
  }

  private fun isNorthAmericaEtf(etfNames: List<String>): Boolean = etfNames.any { it.contains("North America", ignoreCase = true) }

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
    if (isNorthAmericaEtf(etfNames)) {
      return buildNorthAmericaPrompt(companyName, tickerInfo, etfContext)
    }
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

  private fun buildNorthAmericaPrompt(
    companyName: String,
    tickerInfo: String,
    etfContext: String,
  ): String =
    """
    This company "$companyName"$tickerInfo is a holding in a North America ETF.
    $etfContext

    Is this company headquartered in Canada or United States?

    ANSWER WITH ONLY: US or CA
    """.trimIndent()
}
