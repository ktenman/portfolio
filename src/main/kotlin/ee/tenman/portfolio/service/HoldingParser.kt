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
    try {
      log.debug("Parsing Holding data from row: {}", element.text())
      val holding = extractHoldingData(element, rank)

      if (holding != null) {
        validateWeightSequence(holding.weight, rank)
        previousWeight = holding.weight
      }

      holding
    } catch (e: Exception) {
      log.warn("Failed to parse holding row: ${element.text()}", e)
      null
    }

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
    if (weight == BigDecimal.ZERO) {
      log.debug("Skipping holding with zero weight: {}", name)
      return null
    }
    return normalizeWeight(weight, name)
  }

  private fun extractLogoUrl(element: SelenideElement): String? {
    val logoUrl =
      try {
        val imgSrc =
          element
            .findAll("img")
            .firstOrNull()
            ?.getAttribute("src")
            ?.takeIf { it.isNotEmpty() }

        imgSrc ?: extractBackgroundImageUrl(element)
      } catch (e: Exception) {
        log.debug("Failed to extract logo URL: ${e.message}")
        null
      }

    logoUrl?.let { log.debug("Found logo URL: $it") }
      ?: log.debug("No logo found in element")

    return logoUrl
  }

  private fun extractBackgroundImageUrl(element: SelenideElement): String? {
    val divs = element.findAll("div")

    for (div in divs) {
      val style = div.getAttribute("style") ?: continue
      if (style.contains("background-image")) {
        val urlPattern = Regex("""url\(['"]?([^'"()]+)['"]?\)""")
        val match = urlPattern.find(style)
        if (match != null) {
          return match.groupValues[1]
        }
      }
    }

    return null
  }

  private fun extractWeightFromAllDivs(allDivs: List<String>): BigDecimal {
    val weightText =
      allDivs
        .firstOrNull { it.contains("%") && !it.contains("$") && !it.contains("\n") }
        ?: return BigDecimal.ZERO

    val cleanedText =
      weightText
        .replace("%", "")
        .replace(",", "")
        .trim()

    return try {
      val parsedWeight = BigDecimal(cleanedText)

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
