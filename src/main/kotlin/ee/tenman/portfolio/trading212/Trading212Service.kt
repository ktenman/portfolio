package ee.tenman.portfolio.trading212

import org.slf4j.LoggerFactory
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class Trading212Service(
  private val trading212Client: Trading212Client,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  companion object {
    val TRADING212_TICKERS =
      mapOf(
        "WTAI:MIL:EUR" to "WTAIm_EQ",
        "VUAA:GER:EUR" to "VUAAm_EQ",
        "SPYL:GER:EUR" to "SPYLa_EQ",
        "QDVE:GER:EUR" to "QDVEd_EQ",
        "XAIX:GER:EUR" to "XAIXd_EQ",
        "CSX5:AEX:EUR" to "CSX5a_EQ",
      )
  }

  @Retryable(backoff = Backoff(delay = 1000, multiplier = 2.0, maxDelay = 5000))
  fun fetchCurrentPrices(): Map<String, BigDecimal> {
    val tickers = TRADING212_TICKERS.values.joinToString(",")

    log.info("Fetching prices for Trading212 tickers: $tickers")

    return try {
      val response = trading212Client.getPrices(tickers)

      val prices = mutableMapOf<String, BigDecimal>()
      TRADING212_TICKERS.forEach { (symbol, ticker) ->
        val priceData = response.data[ticker]
        if (priceData != null) {
          prices[symbol] = priceData.bid
          log.debug("Fetched price for {}: {}", symbol, priceData.bid)
        } else {
          log.warn("No price data found for ticker: $ticker (symbol: $symbol)")
        }
      }

      log.info("Successfully fetched prices for ${prices.size}/${TRADING212_TICKERS.size} instruments")
      prices
    } catch (e: Exception) {
      log.error("Failed to fetch prices from Trading212", e)
      throw e
    }
  }
}
