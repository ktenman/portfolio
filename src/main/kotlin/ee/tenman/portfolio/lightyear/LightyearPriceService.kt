package ee.tenman.portfolio.lightyear

import ee.tenman.portfolio.dto.HoldingData
import org.slf4j.LoggerFactory
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class LightyearPriceService(
  private val lightyearPriceClient: LightyearPriceClient,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  companion object {
    val LIGHTYEAR_INSTRUMENTS =
      mapOf(
        "VUAA:GER:EUR" to "1eda4008-c9e6-6bde-b60a-654bcfbd8ac3",
        "QDVE:GER:EUR" to "1ef27f9a-bde6-6dda-a873-3946ca86bd5c",
        "WTAI:MIL:EUR" to "1f02fdcc-38f9-67b8-ad1b-a71ae2564bd4",
        "SPYL:GER:EUR" to "1ef66aa4-5a18-65da-ab3f-c9f7567377e0",
        "XAIX:GER:EUR" to "1ef66ace-af0a-6d81-ab3f-c9f7567377e0",
        "CSX5:AEX:EUR" to "1ecf2dbe-e840-6165-8df4-d975f6a704cf",
        "VNRT:AEX:EUR" to "1ecf2da3-e426-68b7-a268-3d0ec5cf88ad",
        "WBIT:GER:EUR" to "1ef3aa4c-5f26-6cf0-8eba-bb4404220dad",
        "VWCE:GER:EUR" to "1eda0a07-10b3-63e0-b568-6deedaa217e7",
        "VNRA:GER:EUR" to "1eda4008-ca17-6926-b60a-654bcfbd8ac3",
      )
  }

  @Retryable(backoff = Backoff(delay = 1000, multiplier = 2.0, maxDelay = 5000))
  fun fetchCurrentPrices(): Map<String, BigDecimal> {
    log.info("Fetching prices for ${LIGHTYEAR_INSTRUMENTS.size} Lightyear instruments")

    val prices = mutableMapOf<String, BigDecimal>()

    LIGHTYEAR_INSTRUMENTS.forEach { (symbol, uuid) ->
      try {
        val path = "/v1/market-data/$uuid/price"
        val response = lightyearPriceClient.getPrice(path)

        prices[symbol] = response.price
        log.debug("Fetched price for {}: {}", symbol, response.price)
      } catch (e: Exception) {
        log.warn("Failed to fetch price for symbol: $symbol", e)
      }
    }

    log.info("Successfully fetched prices for ${prices.size}/${LIGHTYEAR_INSTRUMENTS.size} instruments")
    return prices
  }

  @Retryable(backoff = Backoff(delay = 1000, multiplier = 2.0, maxDelay = 5000))
  fun fetchHoldingsAsDto(symbol: String): List<HoldingData> {
    val apiHoldings = fetchHoldingsRaw(symbol)
    val instrumentMap = fetchInstrumentsBatch(apiHoldings)

    return apiHoldings.mapIndexed { index, holding ->
      val instrument = holding.instrumentId?.let { instrumentMap[it] }
      HoldingData(
        name = holding.name,
        ticker = instrument?.symbol,
        sector = instrument?.summary?.sector,
        weight = BigDecimal.valueOf(holding.value),
        rank = index + 1,
        logoUrl = instrument?.logo,
      )
    }
  }

  private fun fetchInstrumentsBatch(
    holdings: List<LightyearHoldingResponse>,
  ): Map<String, LightyearInstrumentResponse> {
    val instrumentIds = holdings.mapNotNull { it.instrumentId }.distinct()
    if (instrumentIds.isEmpty()) return emptyMap()

    log.info("Fetching {} instrument details in batches", instrumentIds.size)
    val cache = mutableMapOf<String, LightyearInstrumentResponse>()
    val batchSize = 100

    instrumentIds.chunked(batchSize).forEachIndexed { batchIndex, batch ->
      runCatching {
        val instruments = lightyearPriceClient.getInstrumentBatch(batch)
        instruments.forEach { cache[it.id] = it }
        log.info("Fetched batch {}/{} ({} instruments)", batchIndex + 1, (instrumentIds.size + batchSize - 1) / batchSize, instruments.size)
      }.onFailure { log.warn("Failed to fetch batch {}: {}", batchIndex + 1, it.message) }
    }

    log.info("Successfully fetched {} of {} instrument details", cache.size, instrumentIds.size)
    return cache
  }

  @Retryable(backoff = Backoff(delay = 1000, multiplier = 2.0, maxDelay = 5000))
  fun fetchHoldingsRaw(symbol: String): List<LightyearHoldingResponse> {
    val uuid = findUuidBySymbol(symbol)
    if (uuid == null) {
      log.warn("No UUID mapping found for symbol: {}", symbol)
      return emptyList()
    }
    log.info("Fetching holdings for {} ({})", symbol, uuid)
    val path = "/v1/market-data/$uuid/fund-info/holdings"
    val holdings = lightyearPriceClient.getHoldings(path)
    log.info("Fetched {} holdings for {}", holdings.size, symbol)
    return holdings
  }

  private fun findUuidBySymbol(symbol: String): String? {
    LIGHTYEAR_INSTRUMENTS[symbol]?.let { return it }
    val matchingKey = LIGHTYEAR_INSTRUMENTS.keys.find { it.startsWith("$symbol:") }
    return matchingKey?.let { LIGHTYEAR_INSTRUMENTS[it] }
  }
}
