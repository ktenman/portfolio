package ee.tenman.portfolio.service

import com.codeborne.selenide.SelenideElement
import ee.tenman.portfolio.dto.HoldingData
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class HoldingParser {
  private val log = LoggerFactory.getLogger(javaClass)
  private var previousWeight: BigDecimal? = null

  fun parseHoldingRow(
    element: SelenideElement,
    rank: Int,
  ): HoldingData? =
    runCatching {
      log.debug("Parsing Holding data from row: {}", element.text())
      extractHoldingData(element, rank)?.also { holding ->
        validateWeightSequence(holding.weight, rank)
        previousWeight = holding.weight
      }
    }.onFailure { e ->
      log.warn("Failed to parse holding row: ${element.text()}", e)
    }.getOrNull()

  fun resetState() {
    previousWeight = null
  }

  private fun extractHoldingData(
    element: SelenideElement,
    rank: Int,
  ): HoldingData? {
    val rowText = element.text()
    val allDivs: List<String> = element.findAll("div").texts().toList()
    log.debug("Row has {} divs. Extracting data from: {}", allDivs.size, rowText)

    val nameParts =
      allDivs
        .firstOrNull()
        ?.split("\n")
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?.takeIf { it.isNotEmpty() }
        ?: return null
    val name = nameParts.first()

    val isNotAvailable = rowText.contains("Instrument is not available")
    val ticker =
      nameParts
      .getOrNull(1)
      ?.removePrefix("$")
      ?.trim()
      .takeUnless { isNotAvailable }
    val sector = nameParts.getOrNull(2).takeUnless { isNotAvailable }

    val weight = extractWeightFromAllDivs(allDivs)
    val normalizedWeight = validateAndNormalizeWeight(weight, name) ?: return null
    val logoUrl = extractLogoUrl(element)

    log.debug(
      "Extracted - Name: {}, Ticker: {}, Sector: {}, Weight: {}, Logo: {}, NotAvailable: {}",
      name,
      ticker,
      sector,
      weight,
      logoUrl,
      isNotAvailable,
    )

    return HoldingData(
      name = name,
      ticker = ticker,
      sector = sector,
      weight = normalizedWeight,
      rank = rank,
      logoUrl = logoUrl,
    )
  }

  private fun validateAndNormalizeWeight(
    weight: BigDecimal,
    name: String,
  ): BigDecimal? {
    if (weight == BigDecimal.ZERO) return null.also { log.debug("Skipping holding with zero weight: {}", name) }
    return normalizeWeight(weight, name)
  }

  private fun extractLogoUrl(element: SelenideElement): String? =
    runCatching {
      element
        .findAll("img")
        .firstOrNull()
        ?.getAttribute("src")
        ?.takeIf { it.isNotEmpty() }
        ?: extractBackgroundImageUrl(element)
    }.onFailure { e ->
      log.debug("Failed to extract logo URL: ${e.message}")
    }.getOrNull()
      ?.also { log.debug("Found logo URL: $it") }
      ?: null.also { log.debug("No logo found in element") }

  private fun extractBackgroundImageUrl(element: SelenideElement): String? {
    val urlPattern = Regex("""url\(['"]?([^'"()]+)['"]?\)""")
    return element.findAll("div").firstNotNullOfOrNull { div ->
      div
        .getAttribute("style")
        ?.takeIf { it.contains("background-image") }
        ?.let { urlPattern.find(it)?.groupValues?.get(1) }
    }
  }

  private fun extractWeightFromAllDivs(allDivs: List<String>): BigDecimal {
    val weightText =
      allDivs.firstOrNull { it.contains("%") && !it.contains("$") && !it.contains("\n") }
      ?: return BigDecimal.ZERO
    val cleanedText = weightText.replace("%", "").replace(",", "").trim()
    return runCatching { BigDecimal(cleanedText) }
      .onFailure { log.warn("Failed to parse weight from: '$weightText'", it) }
      .getOrNull()
      ?.let { parsed ->
        if (parsed > BigDecimal(10000)) {
          log.warn("Parsed weight from '{}' exceeds 10000%, likely incorrect data", weightText)
          BigDecimal.ZERO
        } else {
          parsed
        }
      }
      ?: BigDecimal.ZERO
  }

  private fun normalizeWeight(
    weight: BigDecimal,
    name: String,
  ): BigDecimal? {
    if (weight <= BigDecimal(100)) return weight
    val maxDivisor = 1_000_000_000
    val result =
      generateSequence(10) { it * 10 }
      .takeWhile { it <= maxDivisor }
      .map { divisor -> divisor to weight.divide(BigDecimal(divisor), 4, java.math.RoundingMode.HALF_UP) }
      .firstOrNull { (_, normalized) -> normalized <= BigDecimal(100) }
    if (result == null) {
      log.warn("Unable to normalize weight {} for holding {}, skipping", weight, name)
      return null
    }
    val (divisor, normalized) = result
    log.info("Normalized weight {} to {} for holding {} (divided by {})", weight, normalized, name, divisor)
    return normalized
  }

  private fun validateWeightSequence(
    currentWeight: BigDecimal,
    rank: Int,
  ) {
    if (previousWeight != null && currentWeight > previousWeight!!) {
      log.warn(
        "Weight sequence anomaly detected! Rank {} has weight {} which is GREATER than previous rank's weight {}. " +
          "Holdings should be in descending order. This may indicate a parsing or normalization bug.",
        rank,
        currentWeight,
        previousWeight,
      )
    }
  }
}
