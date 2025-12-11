package ee.tenman.portfolio.lightyear

import ee.tenman.portfolio.dto.HoldingData
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.slf4j.LoggerFactory
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import java.math.BigDecimal

private val EUROPEAN_CURRENCY_PATTERN = Regex("""^(.+?)\s+(Fr\.|DKr|Skr|Nkr|Ft|zł)\s*([A-Z0-9]+)·(.+)$""")

@Service
class LightyearHoldingsService(
  private val lightyearHoldingsClient: LightyearHoldingsClient,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  @Retryable(backoff = Backoff(delay = 1000, multiplier = 2.0, maxDelay = 5000))
  fun fetchHoldings(
    path: String,
    page: Int,
  ): List<HoldingData> {
    log.info("Fetching holdings for ETF: {} page: {}", path, page)

    return try {
      val htmlContent = lightyearHoldingsClient.getHoldings(path, page)
      parseHoldings(htmlContent, page)
    } catch (e: Exception) {
      log.error("Failed to fetch holdings for ETF: {} page: {}", path, page, e)
      throw e
    }
  }

  fun parseHoldings(
    htmlContent: String,
    page: Int,
  ): List<HoldingData> {
    val document = Jsoup.parse(htmlContent)
    val rows =
      document
        .select(".table-row")
        .filter { it.text().contains("%") }

    val holdings = mutableListOf<HoldingData>()
    val baseRank = (page - 1) * 45

    rows.forEachIndexed { index, row ->
      try {
        parseHoldingRow(row, baseRank + index + 1)?.let { holdings.add(it) }
      } catch (e: Exception) {
        log.warn("Failed to parse holding row at index {}: {}", index, e.message)
      }
    }

    log.info("Parsed {} holdings from page {}", holdings.size, page)
    return holdings
  }

  private fun parseHoldingRow(
    element: Element,
    rank: Int,
  ): HoldingData? {
    val rowData = extractValidatedRowData(element) ?: return null

    val weight = parseWeight(rowData.weightText)
    val normalizedWeight = validateAndNormalizeWeight(weight, rowData.name) ?: return null

    val logoUrl = extractLogoUrl(element)

    log.debug(
      "Extracted - Name: {}, Ticker: {}, Sector: {}, Weight: {}, Logo: {}, Rank: {}",
      rowData.name,
      rowData.ticker,
      rowData.sector,
      normalizedWeight,
      logoUrl,
      rank,
    )

    return HoldingData(
      name = rowData.name,
      ticker = rowData.ticker,
      sector = rowData.sector,
      weight = normalizedWeight,
      rank = rank,
      logoUrl = logoUrl,
    )
  }

  private fun extractValidatedRowData(element: Element): ValidatedRowData? {
    val parsedData = parseBasicRowData(element) ?: return null
    val rawName = parsedData.nameParts.first()
    val isNotAvailable = element.text().contains("Instrument is not available")
    EUROPEAN_CURRENCY_PATTERN.matchEntire(rawName)?.let { match ->
      return ValidatedRowData(
        name = match.groupValues[1],
        ticker = match.groupValues[3],
        sector = match.groupValues[4],
        weightText = parsedData.weightText,
      )
    }
    val name =
      when {
      isNotAvailable -> rawName.replace(Regex("""Instrument is not available.*"""), "").trim()
      else -> rawName
    }
    val tickerAndSector = parsedData.nameParts.getOrNull(1)
    val ticker =
      tickerAndSector
        ?.removePrefix("$")
        ?.removePrefix("€")
        ?.removePrefix("£")
        ?.substringBefore("·")
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        .takeUnless { isNotAvailable }
    val sectorFromTickerField = tickerAndSector?.substringAfter("·", "")?.trim()?.takeIf { it.isNotBlank() }
    val sector = (parsedData.nameParts.getOrNull(2) ?: sectorFromTickerField).takeUnless { isNotAvailable }
    return ValidatedRowData(name, ticker, sector, parsedData.weightText)
  }

  private fun parseBasicRowData(element: Element): BasicRowData? {
    val allDivs = element.select("div")
    if (allDivs.size < 4) {
      log.debug("Row has insufficient divs: {}", allDivs.size)
      return null
    }

    val allDivTexts = allDivs.map { it.text().trim() }.filter { it.isNotEmpty() }
    if (allDivTexts.isEmpty()) return null

    val nameParts = extractNameParts(allDivTexts)
    val weightText = extractWeightText(allDivs)

    return if (nameParts.isEmpty() || weightText == null) null else BasicRowData(nameParts, weightText)
  }

  private fun extractNameParts(allDivTexts: List<String>): List<String> {
    val firstDivText = allDivTexts[0]
    return if (firstDivText.contains("\n")) {
      firstDivText.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
    } else {
      parseCompoundText(firstDivText, allDivTexts)
    }
  }

  private fun extractWeightText(allDivs: org.jsoup.select.Elements): String? =
    allDivs
      .map { it.text().trim() }
      .firstOrNull { it.contains("%") && !it.contains("$") && !it.contains("\n") }

  private fun parseWeight(weightText: String): BigDecimal {
    val percentagePattern = Regex("""(\d+\.?\d*)\s*%""")
    val match = percentagePattern.find(weightText)

    if (match == null) {
      log.warn("No percentage pattern found in: '$weightText'")
      return BigDecimal.ZERO
    }

    val numericValue = match.groupValues[1]

    return try {
      val parsedWeight = BigDecimal(numericValue)

      if (parsedWeight > BigDecimal(10000)) {
        log.warn(
          "Parsed weight {} from '{}' exceeds 10000%, likely incorrect data (market cap/volume). Returning ZERO.",
          parsedWeight,
          weightText,
        )
        return BigDecimal.ZERO
      }

      parsedWeight
    } catch (e: NumberFormatException) {
      log.warn("Failed to parse weight from: '$weightText'", e)
      BigDecimal.ZERO
    }
  }

  private fun validateAndNormalizeWeight(
    weight: BigDecimal,
    name: String,
  ): BigDecimal? {
    if (weight == BigDecimal.ZERO) {
      log.debug("Skipping holding with zero weight: {}", name)
      return null
    }
    return normalizeWeight(weight, name)
  }

  private fun normalizeWeight(
    weight: BigDecimal,
    name: String,
  ): BigDecimal? {
    if (weight <= BigDecimal(100)) {
      return weight
    }

    var normalized = weight
    var divisor = 1

    while (normalized > BigDecimal(100)) {
      divisor *= 10
      normalized = weight.divide(BigDecimal(divisor), 4, java.math.RoundingMode.HALF_UP)

      if (divisor > 1_000_000_000) {
        log.warn("Unable to normalize weight {} for holding {}, skipping", weight, name)
        return null
      }
    }

    log.info("Normalized weight {} to {} for holding {} (divided by {})", weight, normalized, name, divisor)
    return normalized
  }

  private fun parseCompoundText(
    firstDivText: String,
    allDivTexts: List<String>,
  ): List<String> =
    tryParseFromDivs(allDivTexts)
      ?: tryParseWithCurrencyIndex(firstDivText)
      ?: parseAsCleanedName(firstDivText)

  private fun tryParseFromDivs(allDivTexts: List<String>): List<String>? {
    if (allDivTexts.size < 3) return null

    val potentialName = allDivTexts.getOrNull(1) ?: ""
    val potentialTicker = allDivTexts.getOrNull(2) ?: ""
    val potentialSector = allDivTexts.getOrNull(3) ?: ""

    val startsWithCurrency =
      potentialTicker.startsWith("$") || potentialTicker.startsWith("€") || potentialTicker.startsWith("£")
    if (startsWithCurrency &&
      !potentialTicker.contains("%") &&
      potentialSector.isNotEmpty() &&
      !potentialSector.contains(
        "%",
      )
    ) {
      return listOf(potentialName, potentialTicker, potentialSector)
    }

    return null
  }

  private fun tryParseWithCurrencyIndex(firstDivText: String): List<String>? {
    val currencyIndexWithSpace = firstDivText.indexOfAny(listOf(" $", " €", " £"))
    if (currencyIndexWithSpace > 0) {
      val name = firstDivText.substring(0, currencyIndexWithSpace).trim()
      val remaining = firstDivText.substring(currencyIndexWithSpace + 1).trim()
      return parseRemainingText(name, remaining)
    }
    val currencyIndexNoSpace = firstDivText.indexOfAny(charArrayOf('$', '€', '£'))
    if (currencyIndexNoSpace <= 0) return null
    val name = firstDivText.substring(0, currencyIndexNoSpace).trim()
    val remaining = firstDivText.substring(currencyIndexNoSpace).trim()
    return parseRemainingText(name, remaining)
  }

  private fun parseRemainingText(
    name: String,
    remaining: String,
  ): List<String> {
    val tickerWithDotPattern = Regex("""([\$€£][A-Z0-9]+)·(.+)""")
    val matchWithDot = tickerWithDotPattern.find(remaining)
    if (matchWithDot != null) {
      val ticker = matchWithDot.groupValues[1]
      val sector = matchWithDot.groupValues[2]
      return listOf(name, ticker, sector)
    }
    val tickerEndPattern = Regex("""([\$€£][A-Z]+)([A-Z][a-z].*)""")
    val match = tickerEndPattern.find(remaining)
    if (match != null) {
      val ticker = match.groupValues[1]
      val sector = match.groupValues[2]
      return listOf(name, ticker, sector)
    }
    val parts = remaining.split(Regex("(?<=[A-Z])(?=[A-Z][a-z])"), limit = 2)
    return if (parts.size == 2) {
      listOf(name, parts[0], parts[1])
    } else {
      listOf(name, remaining)
    }
  }

  private fun parseAsCleanedName(firstDivText: String): List<String> {
    val cleanedName = firstDivText.replace(Regex("""\s+\d+\.?\d*%.*"""), "").trim()
    return listOf(cleanedName)
  }

  private fun extractLogoUrl(element: Element): String? {
    val imgSrc =
      element
        .select("img")
        .firstOrNull()
        ?.attr("src")
        ?.takeIf { it.isNotEmpty() }
    if (imgSrc != null) {
      return imgSrc
    }

    element.select("div").forEach { div ->
      val style = div.attr("style")
      if (style.contains("background-image")) {
        val urlPattern = Regex("""url\(['"]?([^'"()]+)['"]?\)""")
        urlPattern
          .find(style)
          ?.groupValues
          ?.get(1)
          ?.let { return it }
      }
    }

    return null
  }
}
