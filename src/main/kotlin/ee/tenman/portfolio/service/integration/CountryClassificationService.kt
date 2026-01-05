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
    private val COUNTRY_NAME_TO_CODE: Map<String, String> =
      VALID_COUNTRY_CODES.associateBy { code ->
        Locale.of("", code).getDisplayCountry(Locale.ENGLISH).lowercase()
      }
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
        Regex("^(bitcoin|ethereum|litecoin|ripple|xrp|solana|cardano|dogecoin|polkadot|avalanche)$", RegexOption.IGNORE_CASE),
        Regex("(stoxx|industrial|s&p).*\\s+(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)\\s+\\d{2}$", RegexOption.IGNORE_CASE),
      )
    private val COUNTRY_EXTRACTION_PATTERN =
      Regex(
        "(?:headquartered|based|located)\\s+in\\s+(\\w+(?:\\s+\\w+){0,3})",
        RegexOption.IGNORE_CASE,
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

  fun classifyBatch(companies: List<CompanyClassificationInput>): Map<Long, CountryClassificationResult> {
    if (companies.isEmpty()) return emptyMap()
    val validCompanies =
      companies.filter { !it.name.isBlank() && !isNonCompanyHolding(it.name) }
    if (validCompanies.isEmpty()) {
      log.info("No valid companies to classify in batch")
      return emptyMap()
    }
    val autoAssigned = mutableMapOf<Long, CountryClassificationResult>()
    val needsLlm = mutableListOf<CompanyClassificationInput>()
    validCompanies.forEach { company ->
      val autoResult = tryAutoAssign(company.etfNames, LogSanitizerUtil.sanitize(company.name))
      if (autoResult != null) {
        autoAssigned[company.holdingId] = autoResult
      } else {
        needsLlm.add(company)
      }
    }
    if (needsLlm.isEmpty()) {
      log.info("All ${autoAssigned.size} companies auto-assigned")
      return autoAssigned
    }
    log.info("Batch classifying ${needsLlm.size} companies (${autoAssigned.size} auto-assigned)")
    val llmResults = classifyBatchWithLlm(needsLlm)
    return autoAssigned + llmResults
  }

  private fun classifyBatchWithLlm(companies: List<CompanyClassificationInput>): Map<Long, CountryClassificationResult> {
    if (!properties.enabled) {
      log.warn("LLM classification disabled")
      return emptyMap()
    }
    val prompt = buildBatchPrompt(companies)
    val response = openRouterClient.classifyWithCountryFallback(prompt)
    if (response == null) {
      log.warn("Batch country classification failed for ${companies.size} companies")
      return emptyMap()
    }
    return parseBatchResponse(response.content, companies, response.model)
  }

  private fun buildBatchPrompt(companies: List<CompanyClassificationInput>): String {
    val companiesList =
      companies
        .mapIndexed { index, company ->
          val tickerInfo = if (!company.ticker.isNullOrBlank()) " (${company.ticker})" else ""
          "${index + 1}. ${company.name}$tickerInfo"
        }.joinToString("\n")
    return """
    Identify the headquarters country for each company. Reply with ONLY the company number and 2-letter ISO country code.

    Rules:
    - Use OPERATIONAL headquarters, not legal incorporation
    - Ferrari = IT, Shell = GB, Airbus = FR
    - One line per company: "1. US" or "2. DE"

    Companies:
    $companiesList

    Reply format (one per line):
    1. XX
    2. XX
    ...
    """.trimIndent()
  }

  private fun parseBatchResponse(
    content: String?,
    companies: List<CompanyClassificationInput>,
    model: ee.tenman.portfolio.domain.AiModel?,
  ): Map<Long, CountryClassificationResult> {
    if (content.isNullOrBlank()) return emptyMap()
    val results = mutableMapOf<Long, CountryClassificationResult>()
    val linePattern = Regex("(\\d+)\\.?\\s*([A-Za-z]{2})")
    content.lines().forEach { line ->
      val match = linePattern.find(line.trim()) ?: return@forEach
      val index = match.groupValues[1].toIntOrNull()?.minus(1) ?: return@forEach
      val countryCode = match.groupValues[2].uppercase()
      if (index !in companies.indices || !VALID_COUNTRY_CODES.contains(countryCode)) return@forEach
      val company = companies[index]
      val countryName = getCountryName(countryCode)
      results[company.holdingId] =
        CountryClassificationResult(countryCode = countryCode, countryName = countryName, model = model)
      log.info("Batch classified '${company.name}' as $countryName ($countryCode)")
    }
    log.info("Successfully parsed ${results.size}/${companies.size} batch results")
    return results
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
    return findCountryCodeByName(content)
  }

  internal fun findCountryCodeByName(name: String): String? {
    val normalized = name.trim().lowercase()
    COUNTRY_NAME_TO_CODE[normalized]?.let { return it }
    val extractedCountry = COUNTRY_EXTRACTION_PATTERN.find(normalized)?.groupValues?.get(1) ?: return null
    return COUNTRY_NAME_TO_CODE[extractedCountry]
  }

  private fun getCountryName(countryCode: String): String = Locale.of("", countryCode).getDisplayCountry(Locale.ENGLISH)

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
