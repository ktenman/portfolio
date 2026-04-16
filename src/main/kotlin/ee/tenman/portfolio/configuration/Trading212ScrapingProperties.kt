package ee.tenman.portfolio.configuration

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "trading212-scraping")
class Trading212ScrapingProperties {
  var symbols: MutableList<Trading212SymbolEntry> = mutableListOf()

  fun findTickerBySymbol(symbol: String): String? = symbols.firstOrNull { it.symbol == symbol }?.ticker

  fun findSymbolByTicker(ticker: String): String? = symbols.firstOrNull { it.ticker == ticker }?.symbol
}
