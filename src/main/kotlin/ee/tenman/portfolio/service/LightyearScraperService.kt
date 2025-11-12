package ee.tenman.portfolio.service

import com.codeborne.selenide.Selenide.elements
import com.codeborne.selenide.Selenide.open
import ee.tenman.portfolio.configuration.LightyearScrapingProperties
import ee.tenman.portfolio.dto.HoldingData
import org.openqa.selenium.By.className
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class LightyearScraperService(
  private val properties: LightyearScrapingProperties,
  private val holdingParser: HoldingParser,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  fun fetchEtfHoldings(etfConfig: LightyearScrapingProperties.EtfConfig): List<HoldingData> {
    holdingParser.resetState()
    val allHoldings = mutableListOf<HoldingData>()
    var rank = 1

    val maxPages = minOf(etfConfig.expectedPages, properties.maxPages)

    for (page in 1..maxPages) {
      val url = "${properties.baseUrl}/${etfConfig.path}/holdings/$page"
      log.info("Fetching page $page of $maxPages for ${etfConfig.symbol}: $url")

      open(url)
      Thread.sleep(properties.pageDelayMs)

      val tableRows =
        elements(className(properties.selectors.holdingsTable))
          .filter { it.text.contains(properties.selectors.rowFilter) }

      val isLastPage = tableRows.isEmpty() || tableRows.size < 45

      if (tableRows.isEmpty()) {
        log.info("No more holdings found on page $page, stopping")
      } else {
        log.info("Found ${tableRows.size} holdings on page $page")

        tableRows.forEach { row ->
          holdingParser.parseHoldingRow(row, rank)?.let { holdingData ->
            allHoldings.add(holdingData)
            rank++
          }
        }

        if (tableRows.size < 45) {
          log.info("Page $page has fewer holdings than expected, likely last page")
        }
      }

      if (isLastPage) break
    }

    return allHoldings
  }
}
