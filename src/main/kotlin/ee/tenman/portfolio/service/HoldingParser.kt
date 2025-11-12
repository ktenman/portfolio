package ee.tenman.portfolio.service

import com.codeborne.selenide.SelenideElement
import ee.tenman.portfolio.dto.HoldingData
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class HoldingParser {
  private val log = LoggerFactory.getLogger(javaClass)

  fun parseHoldingRow(
    element: SelenideElement,
    rank: Int,
  ): HoldingData? =
    try {
      log.debug("Parsing Holding data from row: {}", element.text())
      extractHoldingData(element, rank)
    } catch (e: Exception) {
      log.warn("Failed to parse holding row: ${element.text()}", e)
      null
    }

  private fun extractHoldingData(
    element: SelenideElement,
    rank: Int,
  ): HoldingData? {
    val rowText = element.text()
    val allDivs: List<String> = element.findAll("div").texts().toList()
    log.debug("Row has {} divs. Extracting data from: {}", allDivs.size, rowText)

    val nameCell = allDivs.firstOrNull() ?: return null
    val nameParts = nameCell.split("\n").map { it.trim() }.filter { it.isNotEmpty() }

    val isNotAvailable = rowText.contains("Instrument is not available")

    return nameParts.getOrNull(0)?.let { name ->
      val ticker =
        nameParts
        .getOrNull(1)
        ?.removePrefix("$")
        ?.trim()
        .takeUnless { isNotAvailable }
      val sector = nameParts.getOrNull(2).takeUnless { isNotAvailable }

      val weight = extractWeightFromAllDivs(allDivs)

      if (weight == BigDecimal.ZERO) {
        log.debug("Skipping holding with zero weight: {}", name)
        return null
      }

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

      HoldingData(
        name = name,
        ticker = ticker,
        sector = sector,
        weight = weight,
        rank = rank,
        logoUrl = logoUrl,
      )
    }
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
      BigDecimal(cleanedText)
    } catch (e: NumberFormatException) {
      log.warn("Failed to parse weight from: '$weightText'", e)
      BigDecimal.ZERO
    }
  }
}
