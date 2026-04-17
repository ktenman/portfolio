package ee.tenman.portfolio.trading212

import ee.tenman.portfolio.configuration.Trading212ScrapingProperties
import org.slf4j.LoggerFactory
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class Trading212Service(
  private val trading212Client: Trading212Client,
  private val scrapingProperties: Trading212ScrapingProperties,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  @Retryable(backoff = Backoff(delay = 1000, multiplier = 2.0, maxDelay = 5000))
  fun fetchCurrentPrices(eligibleSymbols: Set<String>): Map<String, BigDecimal> {
    if (eligibleSymbols.isEmpty()) {
      log.info("No Trading212-provider instruments to price, skipping fetch")
      return emptyMap()
    }
    val entries = scrapingProperties.symbols.filter { it.symbol in eligibleSymbols }
    if (entries.isEmpty()) {
      log.warn("No Trading212 symbols configured for eligible instruments: $eligibleSymbols")
      return emptyMap()
    }
    val tickers = entries.joinToString(",") { it.ticker }
    log.info("Fetching prices for Trading212 tickers: $tickers")
    val response = trading212Client.getPrices(tickers)
    val prices =
      entries
        .mapNotNull { entry ->
          val priceData = response.data[entry.ticker]
          if (priceData == null) {
            log.warn("No price data found for ticker: ${entry.ticker} (symbol: ${entry.symbol})")
            return@mapNotNull null
          }
          entry.symbol to priceData.bid
        }.toMap()
    log.info("Successfully fetched prices for ${prices.size}/${entries.size} instruments")
    return prices
  }
}
