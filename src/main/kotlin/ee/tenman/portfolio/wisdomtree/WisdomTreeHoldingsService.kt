package ee.tenman.portfolio.wisdomtree

import org.jsoup.Jsoup
import org.slf4j.LoggerFactory
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class WisdomTreeHoldingsService(
  private val wisdomTreeHoldingsClient: WisdomTreeHoldingsClient,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  companion object {
    const val WTAI_ETF_ID = "4250077A-2962-435E-BD51-5B9A9465FD66"
  }

  @Retryable(backoff = Backoff(delay = 1000, multiplier = 2.0, maxDelay = 5000))
  fun fetchHoldings(etfId: String = WTAI_ETF_ID): List<WisdomTreeHolding> {
    log.info("Fetching holdings for ETF: $etfId")

    return try {
      val htmlContent = wisdomTreeHoldingsClient.getHoldings(etfId)
      parseHoldings(htmlContent)
    } catch (e: Exception) {
      log.error("Failed to fetch holdings for ETF: $etfId", e)
      throw e
    }
  }

  fun parseHoldings(htmlContent: String): List<WisdomTreeHolding> {
    val document = Jsoup.parse(htmlContent)
    val rows = document.select("table.table-striped-customized tbody tr")

    val holdings = mutableListOf<WisdomTreeHolding>()

    rows.forEach { row ->
      val cells = row.select("td")
      if (cells.size >= 4) {
        val name = extractCompanyName(cells[0].text())
        val ticker = cells[1].text().trim()
        val countryCode = cells[2].text().trim()
        val weightText = cells[3].text().trim()

        val weight = parseWeight(weightText)

        if (name.isNotEmpty() && ticker.isNotEmpty() && weight != null && weight > BigDecimal.ZERO) {
          holdings.add(
            WisdomTreeHolding(
              name = name,
              ticker = ticker,
              countryCode = countryCode,
              weight = weight,
            ),
          )
        } else {
          log.debug("Skipping invalid row: name='$name', ticker='$ticker', weight=$weight")
        }
      }
    }

    log.info("Parsed ${holdings.size} holdings from ETF")
    return holdings
  }

  private fun extractCompanyName(fullText: String): String {
    val parts = fullText.split(". ", limit = 2)
    return if (parts.size == 2) parts[1].trim() else fullText.trim()
  }

  private fun parseWeight(weightText: String): BigDecimal? =
    try {
      val cleanedWeight = weightText.replace("%", "").trim()
      BigDecimal(cleanedWeight)
    } catch (e: Exception) {
      log.warn("Failed to parse weight: $weightText", e)
      null
    }

  fun extractTickerSymbol(ticker: String): String {
    val parts = ticker.split(" ")
    return if (parts.isNotEmpty()) parts[0] else ticker
  }
}
