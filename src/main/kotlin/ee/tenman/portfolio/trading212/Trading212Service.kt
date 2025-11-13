package ee.tenman.portfolio.trading212

import ee.tenman.portfolio.service.InstrumentService
import org.slf4j.LoggerFactory
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service

@Service
class Trading212Service(
  private val trading212Client: Trading212Client,
  private val instrumentService: InstrumentService,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  companion object {
    private val TRADING212_TICKERS =
      mapOf(
        "WTAI:MIL:EUR" to "WTAIm_EQ",
        "VUAA:GER:EUR" to "VUAAm_EQ",
        "SPYL:GER:EUR" to "SPYLa_EQ",
        "QDVE:GER:EUR" to "QDVEd_EQ",
        "XAIX:GER:EUR" to "XAIXd_EQ",
      )
  }

  @Retryable(backoff = Backoff(delay = 1000, multiplier = 2.0, maxDelay = 5000))
  fun updateCurrentPrices() {
    val tickers = TRADING212_TICKERS.values.joinToString(",")

    log.info("Fetching prices for Trading212 tickers: $tickers")

    try {
      val response = trading212Client.getPrices(tickers)

      val allInstruments = instrumentService.getAllInstrumentsWithoutFiltering()
      val instrumentsBySymbol = allInstruments.associateBy { it.symbol }

      var updatedCount = 0
      TRADING212_TICKERS.forEach { (symbol, ticker) ->
        val priceData = response.data[ticker]
        if (priceData != null) {
          val instrument = instrumentsBySymbol[symbol]
          if (instrument != null) {
            instrument.currentPrice = priceData.bid
            instrumentService.saveInstrument(instrument)
            log.debug("Updated price for {}: {}", symbol, priceData.bid)
            updatedCount++
          } else {
            log.warn("Instrument not found for symbol: $symbol")
          }
        } else {
          log.warn("No price data found for ticker: $ticker (symbol: $symbol)")
        }
      }

      log.info("Successfully updated prices for $updatedCount/${TRADING212_TICKERS.size} instruments")
    } catch (e: Exception) {
      log.error("Failed to fetch prices from Trading212", e)
      throw e
    }
  }

}
