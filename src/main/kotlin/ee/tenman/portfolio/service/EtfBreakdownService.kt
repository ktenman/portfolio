package ee.tenman.portfolio.service

import ee.tenman.portfolio.domain.EtfPosition
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.domain.TransactionType
import ee.tenman.portfolio.dto.EtfHoldingBreakdownDto
import ee.tenman.portfolio.repository.EtfPositionRepository
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.repository.PortfolioTransactionRepository
import ee.tenman.portfolio.util.LogSanitizerUtil
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode

@Service
class EtfBreakdownService(
  private val instrumentRepository: InstrumentRepository,
  private val etfPositionRepository: EtfPositionRepository,
  private val transactionRepository: PortfolioTransactionRepository,
  private val dailyPriceService: DailyPriceService,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  @Cacheable(
    "etf:breakdown",
    key =
      "T(java.util.Objects).hash(" +
      "#etfSymbols != null && !#etfSymbols.isEmpty() ? new java.util.TreeSet(#etfSymbols).toString() : 'all', " +
      "#platforms != null && !#platforms.isEmpty() ? new java.util.TreeSet(#platforms).toString() : 'all')",
      unless = "#result.isEmpty()",
  )
  fun getHoldingsBreakdown(
    etfSymbols: List<String>? = null,
    platforms: List<String>? = null,
  ): List<EtfHoldingBreakdownDto> {
    val platformFilter = parsePlatformFilters(platforms)
    val lightyearEtfs = getLightyearEtfs(etfSymbols, platformFilter)
    log.info("Found ${lightyearEtfs.size} ETFs: ${lightyearEtfs.map { it.symbol }}")

    if (lightyearEtfs.isEmpty()) {
      log.warn("No Lightyear ETFs found")
      return emptyList()
    }

    val actualPortfolioTotal = calculateActualPortfolioTotal(lightyearEtfs, platformFilter)
    log.info("Actual portfolio total value from transactions: $actualPortfolioTotal")

    val holdingsMap = buildHoldingsMap(lightyearEtfs, platformFilter)
    log.info("Built holdings map with ${holdingsMap.size} unique holdings")

    val result = aggregateByHolding(holdingsMap, actualPortfolioTotal)
    log.info("Returning ${result.size} holdings in breakdown")
    return result
  }

  private fun parsePlatformFilters(platforms: List<String>?): Set<Platform>? {
    if (platforms.isNullOrEmpty()) return null
    val parsed = platforms.mapNotNull { runCatching { Platform.valueOf(it.uppercase()) }.getOrNull() }
    return parsed.toSet().takeIf { it.isNotEmpty() }
  }

  @CacheEvict("etf:breakdown", allEntries = true)
  fun evictBreakdownCache() {
    log.info("Evicting ETF breakdown cache")
  }

  private fun getLightyearEtfs(
    etfSymbols: List<String>? = null,
    platformFilter: Set<Platform>? = null,
  ): List<Instrument> {
    val lightyearInstruments = instrumentRepository.findByProviderName(ProviderName.LIGHTYEAR)
    val ftInstruments = instrumentRepository.findByProviderName(ProviderName.FT)

    log.info("Found ${lightyearInstruments.size} LIGHTYEAR instruments and ${ftInstruments.size} FT instruments")

    var allInstruments = lightyearInstruments + ftInstruments

    if (!etfSymbols.isNullOrEmpty()) {
      allInstruments = allInstruments.filter { it.symbol in etfSymbols }
      log.info("Filtered to ${allInstruments.size} instruments matching symbols: {}", LogSanitizerUtil.sanitize(etfSymbols))
    }

    val withHoldings = allInstruments.filter { hasEtfHoldings(it.id) }
    log.info("${withHoldings.size} instruments have ETF holdings data: ${withHoldings.map { it.symbol }}")

    val withActivePositions = withHoldings.filter { hasActiveHoldings(it.id, platformFilter) }
    log.info("${withActivePositions.size} instruments have active positions: ${withActivePositions.map { it.symbol }}")

    return withActivePositions
  }

  private fun hasEtfHoldings(instrumentId: Long): Boolean = etfPositionRepository.findLatestPositionsByEtfId(instrumentId).isNotEmpty()

  private fun hasActiveHoldings(
    instrumentId: Long,
    platformFilter: Set<Platform>? = null,
  ): Boolean {
    val netQuantity = calculateNetQuantity(instrumentId, platformFilter)
    log.debug("Instrument $instrumentId has net quantity: $netQuantity")
    return netQuantity > BigDecimal.ZERO
  }

  private fun calculateNetQuantity(
    instrumentId: Long,
    platformFilter: Set<Platform>? = null,
  ): BigDecimal {
    var transactions = transactionRepository.findAllByInstrumentId(instrumentId)
    if (platformFilter != null) {
      transactions = transactions.filter { platformFilter.contains(it.platform) }
    }
    return transactions.fold(BigDecimal.ZERO) { acc, tx ->
      when (tx.transactionType) {
        TransactionType.BUY -> acc.add(tx.quantity)
        TransactionType.SELL -> acc.subtract(tx.quantity)
      }
    }
  }

  private fun getPlatformsForInstrument(
    instrumentId: Long,
    platformFilter: Set<Platform>? = null,
  ): Set<Platform> {
    var transactions = transactionRepository.findAllByInstrumentId(instrumentId)
    if (platformFilter != null) {
      transactions = transactions.filter { platformFilter.contains(it.platform) }
    }
    return transactions.map { it.platform }.toSet()
  }

  private fun buildHoldingsMap(
    etfs: List<Instrument>,
    platformFilter: Set<Platform>? = null,
  ): Map<HoldingKey, HoldingValue> {
    val allHoldings = etfs.flatMap { etf -> buildHoldingsForEtf(etf, platformFilter) }
    return aggregateHoldings(allHoldings)
  }

  private fun buildHoldingsForEtf(
    etf: Instrument,
    platformFilter: Set<Platform>?,
  ): List<InternalHoldingData> {
    val positions = etfPositionRepository.findLatestPositionsByEtfId(etf.id)
    val etfQuantity = calculateNetQuantity(etf.id, platformFilter)
    val etfPrice = getCurrentPrice(etf)
    val etfPlatforms = getPlatformsForInstrument(etf.id, platformFilter)
    return positions.map { position ->
      InternalHoldingData(
        ticker =
          position.holding.ticker
          ?.uppercase()
          ?.trim()
          ?.takeIf { it.isNotBlank() },
          name = position.holding.name.trim(),
        sector =
          position.holding.sector
          ?.trim()
          ?.takeIf { it.isNotBlank() },
          value = calculateHoldingValue(position, etfQuantity, etfPrice),
        etfSymbol = etf.symbol,
        platforms = etfPlatforms,
      )
    }
  }

  private fun aggregateHoldings(holdings: List<InternalHoldingData>): Map<HoldingKey, HoldingValue> =
    holdings
      .groupBy { holding -> buildHoldingGroupKey(holding) }
      .entries
      .associate { (_, groupedHoldings) -> buildHoldingEntry(groupedHoldings) }

  private fun buildHoldingGroupKey(holding: InternalHoldingData): String =
    if (!holding.ticker.isNullOrBlank()) {
      "ticker:${holding.ticker}"
    } else {
      "name:${holding.name.lowercase()}:${holding.sector?.lowercase().orEmpty()}"
    }

  private fun buildHoldingEntry(groupedHoldings: List<InternalHoldingData>): Pair<HoldingKey, HoldingValue> {
    val key =
      HoldingKey(
      ticker = groupedHoldings.firstOrNull { !it.ticker.isNullOrBlank() }?.ticker,
      name = groupedHoldings.maxByOrNull { it.name.length }!!.name,
      sector = groupedHoldings.mapNotNull { it.sector }.maxByOrNull { it.length },
    )
    val value =
      HoldingValue(
      totalValue = groupedHoldings.fold(BigDecimal.ZERO) { acc, h -> acc.add(h.value) },
      etfSymbols = groupedHoldings.map { it.etfSymbol }.toMutableSet(),
      platforms = groupedHoldings.flatMap { it.platforms }.toMutableSet(),
    )
    return key to value
  }

  private fun getCurrentPrice(instrument: Instrument): BigDecimal {
    instrument.currentPrice?.takeIf { it > BigDecimal.ZERO }?.let { return it }
    return runCatching { dailyPriceService.getPrice(instrument, java.time.LocalDate.now()) }
      .onFailure { log.warn("No price found for ${instrument.symbol}, using zero", it) }
      .getOrDefault(BigDecimal.ZERO)
  }

  private fun calculateHoldingValue(
    position: EtfPosition,
    etfQuantity: BigDecimal,
    etfPrice: BigDecimal,
  ): BigDecimal {
    val etfValue = etfQuantity.multiply(etfPrice)
    val weightDecimal = position.weightPercentage.divide(BigDecimal(100), 10, RoundingMode.HALF_UP)
    return etfValue.multiply(weightDecimal)
  }

  private fun calculateActualPortfolioTotal(
    etfs: List<Instrument>,
    platformFilter: Set<Platform>? = null,
  ): BigDecimal =
    etfs.fold(BigDecimal.ZERO) { acc, etf ->
      val quantity = calculateNetQuantity(etf.id, platformFilter)
      val price = getCurrentPrice(etf)
      acc.add(quantity.multiply(price))
    }

  private fun aggregateByHolding(
    holdingsMap: Map<HoldingKey, HoldingValue>,
    portfolioTotal: BigDecimal,
  ): List<EtfHoldingBreakdownDto> {
    if (portfolioTotal == BigDecimal.ZERO) {
      log.warn("Portfolio total is zero, cannot calculate percentages")
      return emptyList()
    }

    val holdingsTotal = holdingsMap.values.fold(BigDecimal.ZERO) { acc, value -> acc.add(value.totalValue) }
    val scaleFactor = portfolioTotal.divide(holdingsTotal, 10, RoundingMode.HALF_UP)

    return holdingsMap
      .map { (key, value) ->
        val scaledValue = value.totalValue.multiply(scaleFactor)
        val percentage = scaledValue.multiply(BigDecimal(100)).divide(portfolioTotal, 4, RoundingMode.HALF_UP)

        EtfHoldingBreakdownDto(
          holdingTicker = key.ticker,
          holdingName = key.name,
          percentageOfTotal = percentage,
          totalValueEur = scaledValue.setScale(2, RoundingMode.HALF_UP),
          holdingSector = key.sector,
          inEtfs = value.etfSymbols.sorted().joinToString(", "),
          numEtfs = value.etfSymbols.size,
          platforms =
            value.platforms
            .map { it.name }
            .sorted()
            .joinToString(", "),
            )
      }.filter { it.totalValueEur > BigDecimal.ZERO }
      .sortedByDescending { it.totalValueEur }
  }
}
