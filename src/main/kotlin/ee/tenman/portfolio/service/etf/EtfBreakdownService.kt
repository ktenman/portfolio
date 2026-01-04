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
import ee.tenman.portfolio.repository.EtfPositionRepository
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.service.infrastructure.CacheInvalidationService
import ee.tenman.portfolio.service.pricing.DailyPriceService
import ee.tenman.portfolio.service.transaction.TransactionCalculationService
import ee.tenman.portfolio.util.LogSanitizerUtil
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.LocalDate

@Service
class EtfBreakdownService(
  private val instrumentRepository: InstrumentRepository,
  private val etfPositionRepository: EtfPositionRepository,
  private val dailyPriceService: DailyPriceService,
  private val cacheInvalidationService: CacheInvalidationService,
  private val holdingAggregationService: HoldingAggregationService,
  private val syntheticEtfCalculationService: SyntheticEtfCalculationService,
  private val transactionCalculationService: TransactionCalculationService,
  private val clock: Clock,
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
    val etfsWithHoldings = getEtfsWithHoldings(etfSymbols, platformFilter)
    log.info("Found ${etfsWithHoldings.size} ETFs with holdings: ${etfsWithHoldings.map { it.symbol }}")
    if (etfsWithHoldings.isEmpty()) {
      log.warn("No ETFs with holdings found")
      return emptyList()
    }
    val allActiveEtfs = getAllActiveEtfs(etfSymbols, platformFilter)
    val actualPortfolioTotal = calculateActualPortfolioTotal(allActiveEtfs, platformFilter)
    val holdingsMap = buildHoldingsMap(etfsWithHoldings, platformFilter)
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
    etfSymbols: List<String>? = null,
    platformFilter: Set<Platform>? = null,
  ): List<Instrument> =
    getFilteredInstruments(etfSymbols)
      .filter { hasEtfHoldings(it.id) }
      .filter { hasActiveHoldings(it.id, platformFilter) }

  private fun getAllActiveEtfs(
    etfSymbols: List<String>? = null,
    platformFilter: Set<Platform>? = null,
  ): List<Instrument> = getFilteredInstruments(etfSymbols).filter { hasActiveHoldings(it.id, platformFilter) }

  private fun getFilteredInstruments(etfSymbols: List<String>? = null): List<Instrument> {
    val allInstruments =
      instrumentRepository.findByProviderName(ProviderName.LIGHTYEAR) +
        instrumentRepository.findByProviderName(ProviderName.FT) +
        instrumentRepository.findByProviderName(ProviderName.SYNTHETIC)
    if (etfSymbols.isNullOrEmpty()) return allInstruments
    return allInstruments.filter { it.symbol in etfSymbols }.also {
      log.info("Filtered to ${it.size} instruments matching symbols: ${LogSanitizerUtil.sanitize(etfSymbols)}")
    }
  }

  private fun hasEtfHoldings(instrumentId: Long): Boolean = etfPositionRepository.findLatestPositionsByEtfId(instrumentId).isNotEmpty()

  private fun hasActiveHoldings(
    instrumentId: Long,
    platformFilter: Set<Platform>? = null,
  ): Boolean {
    val instrument = instrumentRepository.findById(instrumentId).orElse(null) ?: return false
    if (instrument.providerName == ProviderName.SYNTHETIC) return syntheticEtfCalculationService.hasActiveHoldings(instrumentId)
    return calculateNetQuantity(instrumentId, platformFilter) > BigDecimal.ZERO
  }

  private fun calculateNetQuantity(
    instrumentId: Long,
    platformFilter: Set<Platform>? = null,
  ): BigDecimal = transactionCalculationService.calculateNetQuantity(instrumentId, platformFilter)

  private fun getPlatformsForInstrument(
    instrumentId: Long,
    platformFilter: Set<Platform>? = null,
  ): Set<Platform> = transactionCalculationService.getPlatformsForInstrument(instrumentId, platformFilter)

  private fun buildHoldingsMap(
    etfs: List<Instrument>,
    platformFilter: Set<Platform>? = null,
  ): Map<HoldingKey, HoldingValue> {
    val allHoldings = etfs.flatMap { etf -> buildHoldingsForEtf(etf, platformFilter) }
    return holdingAggregationService.aggregateHoldings(allHoldings)
  }

  private fun buildHoldingsForEtf(
    etf: Instrument,
    platformFilter: Set<Platform>?,
  ): List<InternalHoldingData> {
    val positions = etfPositionRepository.findLatestPositionsByEtfId(etf.id)
    if (etf.providerName == ProviderName.SYNTHETIC) return syntheticEtfCalculationService.buildHoldings(positions, etf.symbol)
    val etfQuantity = calculateNetQuantity(etf.id, platformFilter)
    val etfPrice = getCurrentPrice(etf)
    val etfPlatforms = getPlatformsForInstrument(etf.id, platformFilter)
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

  private fun getCurrentPrice(instrument: Instrument): BigDecimal {
    instrument.currentPrice?.takeIf { it > BigDecimal.ZERO }?.let { return it }
    return runCatching { dailyPriceService.getPrice(instrument, LocalDate.now(clock)) }
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
  ): BigDecimal {
    val syntheticEtfs = etfs.filter { it.providerName == ProviderName.SYNTHETIC }
    val syntheticValues = syntheticEtfCalculationService.calculateTotalValue(syntheticEtfs)
    val regularValues =
      etfs
        .filter { it.providerName != ProviderName.SYNTHETIC }
        .fold(BigDecimal.ZERO) { acc, etf ->
          acc.add(calculateNetQuantity(etf.id, platformFilter).multiply(getCurrentPrice(etf)))
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
    val allInstruments =
      instrumentRepository.findByProviderName(ProviderName.LIGHTYEAR) +
        instrumentRepository.findByProviderName(ProviderName.FT)
    return allInstruments.map { instrument -> buildDiagnosticDto(instrument) }
  }

  private fun buildDiagnosticDto(instrument: Instrument): EtfDiagnosticDto {
    val positions = etfPositionRepository.findLatestPositionsByEtfId(instrument.id)
    val transactionStats = transactionCalculationService.getTransactionStats(instrument.id)
    val netQuantity = calculateNetQuantity(instrument.id, null)
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
