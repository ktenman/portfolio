package ee.tenman.portfolio.trading212

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.atomic.AtomicReference

@Service
class Trading212CatalogueService(
  private val catalogueClient: Trading212CatalogueClient,
) {
  private val log = LoggerFactory.getLogger(javaClass)
  private val cache = AtomicReference<Map<String, Trading212Instrument>>(emptyMap())

  fun getInstrumentByTicker(ticker: String): Trading212Instrument? {
    val snapshot = cache.get()
    if (snapshot.isNotEmpty()) return snapshot[ticker]
    val loaded = load()
    cache.set(loaded)
    return loaded[ticker]
  }

  private fun load(): Map<String, Trading212Instrument> =
    runCatching {
      val instruments = catalogueClient.fetchInstruments()
      log.info("Fetched ${instruments.size} instruments from Trading212 catalogue")
      instruments.associateBy { it.ticker }
    }.getOrElse {
      log.error("Failed to fetch Trading212 instrument catalogue", it)
      emptyMap()
    }
}
