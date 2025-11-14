package ee.tenman.portfolio.configuration

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "scraping.lightyear")
data class LightyearScrapingProperties(
  var baseUrl: String = "https://lightyear.com/en/etf",
  var maxPages: Int = 5,
  var pageDelayMs: Long = 2000,
  var selectors: Selectors = Selectors(),
  var etfs: List<EtfConfig> = emptyList(),
) {
  data class Selectors(
    var holdingsTable: String = "table-row",
    var rowFilter: String = "%",
    var cells: CellSelectors = CellSelectors(),
  )

  data class CellSelectors(
    var name: String = "div:nth-child(1)",
    var ticker: String = "div:nth-child(2)",
    var sector: String = "div:nth-child(3)",
    var weight: String = "div:nth-child(4)",
    var marketCap: String = "div:nth-child(5)",
  )

  data class EtfConfig(
    var symbol: String = "",
    var path: String = "",
    var expectedPages: Int = 1,
    var skipHoldings: Boolean = false,
  )
}
