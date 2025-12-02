package ee.tenman.portfolio.service

import ee.tenman.portfolio.configuration.LightyearScrapingProperties
import ee.tenman.portfolio.dto.HoldingData
import ee.tenman.portfolio.lightyear.LightyearHoldingsService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class LightyearScraperService(
  private val properties: LightyearScrapingProperties,
  private val lightyearHoldingsService: LightyearHoldingsService,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  fun fetchEtfHoldings(etfConfig: LightyearScrapingProperties.EtfConfig): List<HoldingData> {
    log.info("Fetching ETF holdings for: {}", etfConfig.symbol)
    val maxPages = minOf(etfConfig.expectedPages, properties.maxPages)
    return fetchPagesSequentially(etfConfig, maxPages).also { holdings ->
      log.info("Fetched {} total holdings for {}", holdings.size, etfConfig.symbol)
    }
  }

  private fun fetchPagesSequentially(
    etfConfig: LightyearScrapingProperties.EtfConfig,
    maxPages: Int,
  ): List<HoldingData> =
    buildList {
    for (page in 1..maxPages) {
      log.info("Fetching page $page of $maxPages for ${etfConfig.symbol}")
      val holdings = lightyearHoldingsService.fetchHoldings(etfConfig.path, page)
      when {
        holdings.isEmpty() -> {
          log.info("No more holdings found on page $page, stopping")
          return@buildList
        }
        holdings.size < 45 -> {
          log.info("Page $page has fewer holdings, likely last page")
          addAll(holdings)
          return@buildList
        }
        else -> addAll(holdings)
      }
    }
  }
}
