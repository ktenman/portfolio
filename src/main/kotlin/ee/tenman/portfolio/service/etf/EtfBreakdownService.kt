package ee.tenman.portfolio.service.etf

import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.ETF_BREAKDOWN_CACHE
import ee.tenman.portfolio.domain.EtfPosition
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.dto.EtfDiagnosticDto
import ee.tenman.portfolio.dto.EtfHoldingBreakdownDto
import ee.tenman.portfolio.model.holding.HoldingKey
import ee.tenman.portfolio.model.holding.HoldingValue
import ee.tenman.portfolio.model.holding.InternalHoldingData
import ee.tenman.portfolio.service.infrastructure.CacheInvalidationService
import ee.tenman.portfolio.service.pricing.DailyPriceService
import ee.tenman.portfolio.service.transaction.InstrumentTransactionData
import ee.tenman.portfolio.service.transaction.TransactionCalculationService
import ee.tenman.portfolio.util.LogSanitizerUtil
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode

@Service
class EtfBreakdownService(
  private val dailyPriceService: DailyPriceService,
  private val cacheInvalidationService: CacheInvalidationService,
  private val holdingAggregationService: HoldingAggregationService,
  private val syntheticEtfCalculationService: SyntheticEtfCalculationService,
  private val transactionCalculationService: TransactionCalculationService,
  private val dataLoader: EtfBreakdownDataLoaderService,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  @Transactional(readOnly = true)
  @Cacheable(
    ETF_BREAKDOWN_CACHE,
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
    val data = dataLoader.loadBreakdownData(etfSymbols, platformFilter)
    if (etfSymbols != null) {
      log.info("Filtered to ${data.instruments.size} instruments matching: ${LogSanitizerUtil.sanitize(etfSymbols)}")
    }
    val etfsWithHoldings = getEtfsWithHoldings(data, platformFilter)
    log.info("Found ${etfsWithHoldings.size} ETFs with holdings: ${etfsWithHoldings.map { it.symbol }}")
    if (etfsWithHoldings.isEmpty()) {
      log.warn("No ETFs with holdings found")
      return emptyList()
    }
    val allActiveEtfs = getAllActiveEtfs(data, platformFilter)
    val actualPortfolioTotal = calculateActualPortfolioTotal(allActiveEtfs, data, platformFilter)
    val holdingsMap = buildHoldingsMap(etfsWithHoldings, data, platformFilter)
    return aggregateByHolding(holdingsMap, actualPortfolioTotal)
  }

  private fun parsePlatformFilters(platforms: List<String>?): Set<Platform>? {
    if (platforms.isNullOrEmpty()) return null
    val parsed = platforms.mapNotNull { runCatching { Platform.valueOf(it.uppercase()) }.getOrNull() }
    return parsed.toSet().takeIf { it.isNotEmpty() }
  }

  fun evictBreakdownCache() {
    cacheInvalidationService.evictEtfBreakdownCache()
    log.info("Evicted ETF breakdown cache")
  }

  private fun getEtfsWithHoldings(
    data: EtfBreakdownData,
    platformFilter: Set<Platform>?,
  ): List<Instrument> =
    data.instruments
      .filter { hasEtfHoldings(it.id, data) }
      .filter { hasActiveHoldings(it, data, platformFilter) }

  private fun getAllActiveEtfs(
    data: EtfBreakdownData,
    platformFilter: Set<Platform>?,
  ): List<Instrument> = data.instruments.filter { hasActiveHoldings(it, data, platformFilter) }

  private fun hasEtfHoldings(
    instrumentId: Long,
    data: EtfBreakdownData,
  ): Boolean = data.positionsByEtfId[instrumentId]?.isNotEmpty() == true

  private fun hasActiveHoldings(
    instrument: Instrument,
    data: EtfBreakdownData,
    platformFilter: Set<Platform>?,
  ): Boolean {
    if (instrument.providerName == ProviderName.SYNTHETIC) {
      return syntheticEtfCalculationService.hasActiveHoldings(instrument.id)
    }
    val transactionData = data.transactionDataByInstrumentId[instrument.id] ?: return false
    return getFilteredQuantity(transactionData, platformFilter) > BigDecimal.ZERO
  }

  private fun getFilteredQuantity(
    transactionData: InstrumentTransactionData,
    platformFilter: Set<Platform>?,
  ): BigDecimal {
    if (platformFilter == null) return transactionData.netQuantity
    return platformFilter
      .mapNotNull { transactionData.quantityByPlatform[it] }
      .fold(BigDecimal.ZERO) { acc, qty -> acc.add(qty) }
  }

  private fun getFilteredPlatforms(
    transactionData: InstrumentTransactionData,
    platformFilter: Set<Platform>?,
  ): Set<Platform> {
    if (platformFilter == null) return transactionData.platforms
    return transactionData.platforms.filter { it in platformFilter }.toSet()
  }

  private fun buildHoldingsMap(
    etfs: List<Instrument>,
    data: EtfBreakdownData,
    platformFilter: Set<Platform>?,
  ): Map<HoldingKey, HoldingValue> {
    val allHoldings = etfs.flatMap { etf -> buildHoldingsForEtf(etf, data, platformFilter) }
    return holdingAggregationService.aggregateHoldings(allHoldings)
  }

  private fun buildHoldingsForEtf(
    etf: Instrument,
    data: EtfBreakdownData,
    platformFilter: Set<Platform>?,
  ): List<InternalHoldingData> {
    val positions = data.positionsByEtfId[etf.id] ?: return emptyList()
    if (etf.providerName == ProviderName.SYNTHETIC) {
      return syntheticEtfCalculationService.buildHoldings(positions, etf.symbol)
    }
    val transactionData = data.allTransactionData[etf.id] ?: return emptyList()
    val etfQuantity = getFilteredQuantity(transactionData, platformFilter)
    val etfPrice = dailyPriceService.getCurrentPrice(etf)
    val etfPlatforms = getFilteredPlatforms(transactionData, platformFilter)
    return positions.map { position -> buildInternalHoldingData(position, etfQuantity, etfPrice, etf.symbol, etfPlatforms) }
  }

  private fun buildInternalHoldingData(
    position: EtfPosition,
    etfQuantity: BigDecimal,
    etfPrice: BigDecimal,
    etfSymbol: String,
    etfPlatforms: Set<Platform>,
  ) = InternalHoldingData(
    holdingUuid = position.holding.uuid,
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
    countryCode =
      position.holding.countryCode
        ?.trim()
        ?.takeIf { it.isNotBlank() },
    countryName =
      position.holding.countryName
        ?.trim()
        ?.takeIf { it.isNotBlank() },
    value = calculateHoldingValue(position, etfQuantity, etfPrice),
    etfSymbol = etfSymbol,
    platforms = etfPlatforms,
  )

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
    data: EtfBreakdownData,
    platformFilter: Set<Platform>?,
  ): BigDecimal {
    val syntheticEtfs = etfs.filter { it.providerName == ProviderName.SYNTHETIC }
    val syntheticValues = syntheticEtfCalculationService.calculateTotalValue(syntheticEtfs)
    val regularValues =
      etfs
        .filter { it.providerName != ProviderName.SYNTHETIC }
        .fold(BigDecimal.ZERO) { acc, etf ->
          val transactionData = data.allTransactionData[etf.id] ?: return@fold acc
          val quantity = getFilteredQuantity(transactionData, platformFilter)
          acc.add(quantity.multiply(dailyPriceService.getCurrentPrice(etf)))
        }
    return regularValues.add(syntheticValues)
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
      .map { (key, value) -> buildBreakdownDto(key, value, scaleFactor, portfolioTotal) }
      .filter { it.totalValueEur > BigDecimal.ZERO }
      .sortedByDescending { it.totalValueEur }
  }

  private fun buildBreakdownDto(
    key: HoldingKey,
    value: HoldingValue,
    scaleFactor: BigDecimal,
    portfolioTotal: BigDecimal,
  ): EtfHoldingBreakdownDto {
    val scaledValue = value.totalValue.multiply(scaleFactor)
    val percentage = scaledValue.multiply(BigDecimal(100)).divide(portfolioTotal, 4, RoundingMode.HALF_UP)
    return EtfHoldingBreakdownDto(
      holdingUuid = key.holdingUuid,
      holdingTicker = key.ticker,
      holdingName = key.name,
      percentageOfTotal = percentage,
      totalValueEur = scaledValue.setScale(2, RoundingMode.HALF_UP),
      holdingSector = key.sector,
      holdingCountryCode = key.countryCode,
      holdingCountryName = key.countryName,
      inEtfs = value.etfSymbols.sorted().joinToString(", "),
      numEtfs = value.etfSymbols.size,
      platforms =
        value.platforms
          .map { it.name }
          .sorted()
          .joinToString(", "),
    )
  }

  @Transactional(readOnly = true)
  fun getDiagnosticData(): List<EtfDiagnosticDto> {
    val data = dataLoader.loadDiagnosticData()
    return data.instruments.map { instrument -> buildDiagnosticDto(instrument, data) }
  }

  private fun buildDiagnosticDto(
    instrument: Instrument,
    data: DiagnosticData,
  ): EtfDiagnosticDto {
    val positions = data.positionsByEtfId[instrument.id] ?: emptyList()
    val transactionStats = transactionCalculationService.getTransactionStats(instrument.id)
    val transactionData = data.transactionDataByInstrumentId[instrument.id]
    val netQuantity = transactionData?.netQuantity ?: BigDecimal.ZERO
    return EtfDiagnosticDto(
      instrumentId = instrument.id,
      symbol = instrument.symbol,
      providerName = instrument.providerName,
      currentPrice = instrument.currentPrice,
      etfPositionCount = positions.size,
      latestSnapshotDate = positions.firstOrNull()?.snapshotDate?.toString(),
      transactionCount = transactionStats.count,
      netQuantity = netQuantity,
      hasEtfHoldings = positions.isNotEmpty(),
      hasActivePosition = netQuantity > BigDecimal.ZERO,
      platforms = transactionStats.platforms,
    )
  }
}
