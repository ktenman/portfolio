package ee.tenman.portfolio.lightyear

import ee.tenman.portfolio.configuration.LightyearScrapingProperties
import ee.tenman.portfolio.dto.HoldingData
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.service.instrument.InstrumentService
import org.slf4j.LoggerFactory
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class LightyearPriceService(
  private val lightyearPriceClient: LightyearPriceClient,
  private val properties: LightyearScrapingProperties,
  private val instrumentRepository: InstrumentRepository,
  private val instrumentService: InstrumentService,
  private val uuidCacheService: LightyearUuidCacheService,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  @Retryable(backoff = Backoff(delay = 1000, multiplier = 2.0, maxDelay = 5000))
  fun fetchCurrentPrices(): Map<String, BigDecimal> {
    val symbols = properties.getAllSymbols()
    log.info("Fetching prices for {} Lightyear instruments", symbols.size)
    val prices = mutableMapOf<String, BigDecimal>()
    symbols.forEach { symbol ->
      val uuid = resolveUuid(symbol)
      if (uuid == null) {
        log.warn("No UUID found for symbol: {}", symbol)
        return@forEach
      }
      runCatching {
        val path = "/v1/market-data/$uuid/price"
        val response = lightyearPriceClient.getPrice(path)
        prices[symbol] = response.price
        log.debug("Fetched price for {}: {}", symbol, response.price)
      }.onFailure { e ->
        log.warn("Failed to fetch price for symbol: {}", symbol, e)
      }
    }
    log.info("Successfully fetched prices for {}/{} instruments", prices.size, symbols.size)
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

  private fun fetchInstrumentsBatch(holdings: List<LightyearHoldingResponse>): Map<String, LightyearInstrumentResponse> {
    val instrumentIds = holdings.mapNotNull { it.instrumentId }.distinct()
    if (instrumentIds.isEmpty()) return emptyMap()
    log.info("Fetching {} instrument details in batches", instrumentIds.size)
    val cache = mutableMapOf<String, LightyearInstrumentResponse>()
    val batchSize = 100
    val totalBatches = (instrumentIds.size + batchSize - 1) / batchSize
    instrumentIds.chunked(batchSize).forEachIndexed { batchIndex, batch ->
      fetchInstrumentBatchWithRetry(batch, batchIndex + 1, totalBatches)?.forEach { cache[it.id] = it }
    }
    log.info("Successfully fetched {} of {} instrument details", cache.size, instrumentIds.size)
    return cache
  }

  private fun fetchInstrumentBatchWithRetry(
    batch: List<String>,
    batchNumber: Int,
    totalBatches: Int,
    maxRetries: Int = 3,
  ): List<LightyearInstrumentResponse>? {
    repeat(maxRetries) { attempt ->
      runCatching {
        val instruments = lightyearPriceClient.getInstrumentBatch(batch)
        log.info("Fetched batch {}/{} ({} instruments)", batchNumber, totalBatches, instruments.size)
        return instruments
      }.onFailure { e ->
        val retriesLeft = maxRetries - attempt - 1
        if (retriesLeft > 0) {
          log.warn("Batch {}/{} failed (attempt {}), retrying: {}", batchNumber, totalBatches, attempt + 1, e.message)
          Thread.sleep((attempt + 1) * 1000L)
        } else {
          log.warn("Batch {}/{} failed after {} attempts: {}", batchNumber, totalBatches, maxRetries, e.message)
        }
      }
    }
    return null
  }

  @Retryable(backoff = Backoff(delay = 1000, multiplier = 2.0, maxDelay = 5000))
  fun fetchHoldingsRaw(symbol: String): List<LightyearHoldingResponse> {
    val uuid = resolveUuid(symbol)
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

  fun resolveUuid(symbol: String): String? =
    properties.findUuidBySymbol(symbol)
      ?: uuidCacheService.getCachedUuid(symbol)
      ?: findUuidFromDatabase(symbol)?.also { uuidCacheService.cacheUuid(symbol, it) }
      ?: lookupUuidFromWeb(symbol)

  private fun findUuidFromDatabase(symbol: String): String? = instrumentRepository.findBySymbol(symbol).orElse(null)?.providerExternalId

  fun lookupUuidFromWeb(symbol: String): String? {
    val lookupSymbol = convertToLightyearSymbol(symbol)
    log.info("Looking up UUID from web for symbol: {} (lookup: {})", symbol, lookupSymbol)
    return runCatching {
      val response = lightyearPriceClient.lookupUuid(lookupSymbol)
      uuidCacheService.cacheUuid(symbol, response.uuid)
      saveUuidToDatabase(symbol, response.uuid)
      log.info("Found UUID {} for symbol {} from web lookup", response.uuid, symbol)
      response.uuid
    }.onFailure { e ->
      log.warn("Failed to lookup UUID for symbol {}: {}", symbol, e.message)
    }.getOrNull()
  }

  fun saveUuidToDatabase(
    symbol: String,
    uuid: String,
  ) {
    runCatching {
      instrumentService.updateProviderExternalId(symbol, uuid)
      log.info("Saved UUID {} for symbol {} to database", uuid, symbol)
    }.onFailure { e ->
      log.warn("Failed to save UUID for symbol {}: {}", symbol, e.message)
    }
  }

  private fun convertToLightyearSymbol(symbol: String): String {
    val parts = symbol.split(":")
    if (parts.size < 2) return symbol
    val ticker = parts[0]
    val exchange = parts[1]
    val lightyearExchange = properties.convertExchangeToLightyear(exchange)
    return "$ticker:$lightyearExchange"
  }
}
