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
    private val NON_COMPANY_REGEXES =
      listOf(
        Regex("^other/?cash$", RegexOption.IGNORE_CASE),
        Regex(
          "^(aud|cad|eur|gbp|ils|jpy|krw|nok|sek|sgd|usd|chf|hkd|nzd|twd|dkk|pln|czk|huf|inr|mxn|zar)\\s+cash$",
          RegexOption.IGNORE_CASE,
        ),
        Regex("^cash\\s+collateral", RegexOption.IGNORE_CASE),
        Regex("^(australian|canadian|hong kong|new zealand|new taiwan|singapore|us)\\s+dollar$", RegexOption.IGNORE_CASE),
        Regex("^(danish krone|japanese yen|pound sterling|swiss franc|euro currency)$", RegexOption.IGNORE_CASE),
        Regex("^bitcoin$", RegexOption.IGNORE_CASE),
        Regex("^ethereum$", RegexOption.IGNORE_CASE),
        Regex("stoxx.*\\d{2}\\s+(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)\\s+\\d{2}$", RegexOption.IGNORE_CASE),
      )
  }

  fun classifyCompanyCountryWithModel(
    companyName: String,
    ticker: String? = null,
    etfNames: List<String> = emptyList(),
  ): CountryClassificationResult? {
    val sanitizedName = LogSanitizerUtil.sanitize(companyName)
    log.info("Classifying country for company: $sanitizedName (ticker: $ticker)")
    if (companyName.isBlank()) {
      log.warn("Blank company name")
      return null
    }
    if (isNonCompanyHolding(companyName)) {
      log.info("Skipping non-company holding: $sanitizedName")
      return null
    }
    return tryAutoAssign(etfNames, sanitizedName) ?: classifyWithLlm(companyName, ticker, etfNames, sanitizedName)
  }

  internal fun isNonCompanyHolding(name: String): Boolean {
    val normalized = name.trim()
    return NON_COMPANY_REGEXES.any { regex -> regex.containsMatchIn(normalized) }
  }

  private fun classifyWithLlm(
    companyName: String,
    ticker: String?,
    etfNames: List<String>,
    sanitizedName: String,
  ): CountryClassificationResult? {
    if (!properties.enabled) {
      log.warn("LLM classification disabled, skipping: $sanitizedName")
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
      log.info("Auto-assigning US for $sanitizedName (in S&P 500 ETF)")
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
      log.warn("All country classification models failed for: $sanitizedName")
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
      if (logUnknownCountry) log.warn("Unknown country from model response: $content")
      return null
    }
    val countryName = getCountryName(countryCode)
    log.info("Classified $sanitizedName as $countryName ($countryCode) using model ${response.model}")
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
    val exactMatch =
      VALID_COUNTRY_CODES.find { code ->
        getCountryName(code).lowercase() == normalized
      }
    if (exactMatch != null) return exactMatch
    return VALID_COUNTRY_CODES.find { code ->
      val countryName = getCountryName(code).lowercase()
      countryName.length >= 4 && normalized.contains(countryName)
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
    $etfContext

    Rules:
    - Use OPERATIONAL headquarters, not legal incorporation country
    - Ferrari (RACE) = IT, Shell (SHEL) = GB, Airbus (AIR) = FR
    - Reply with ONLY the 2-letter ISO code (US, GB, DE, FR, etc.)
    - NO sentences, NO explanations, NO punctuation - just 2 letters
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
