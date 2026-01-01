package ee.tenman.portfolio.service.etf

import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.ETF_BREAKDOWN_CACHE
import ee.tenman.portfolio.domain.EtfPosition
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.domain.TransactionType
import ee.tenman.portfolio.dto.EtfDiagnosticDto
import ee.tenman.portfolio.dto.EtfHoldingBreakdownDto
import ee.tenman.portfolio.model.holding.HoldingKey
import ee.tenman.portfolio.model.holding.HoldingValue
import ee.tenman.portfolio.model.holding.InternalHoldingData
import ee.tenman.portfolio.model.holding.SyntheticHoldingValue
import ee.tenman.portfolio.repository.EtfPositionRepository
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.repository.PortfolioTransactionRepository
import ee.tenman.portfolio.service.infrastructure.CacheInvalidationService
import ee.tenman.portfolio.service.pricing.DailyPriceService
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
  private val transactionRepository: PortfolioTransactionRepository,
  private val dailyPriceService: DailyPriceService,
  private val cacheInvalidationService: CacheInvalidationService,
  private val clock: Clock,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  companion object {
    private val TREZOR_SUFFIX_REGEX = Regex("\\s*\\(Trezor\\)\\s*$", RegexOption.IGNORE_CASE)
  }

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
    log.info("Found ${allActiveEtfs.size} total active ETFs: ${allActiveEtfs.map { it.symbol }}")

    val actualPortfolioTotal = calculateActualPortfolioTotal(allActiveEtfs, platformFilter)
    log.info("Actual portfolio total value from all ETFs: $actualPortfolioTotal")

    val holdingsMap = buildHoldingsMap(etfsWithHoldings, platformFilter)
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

  fun evictBreakdownCache() {
    cacheInvalidationService.evictEtfBreakdownCache()
    log.info("Evicted ETF breakdown cache")
  }

  private fun getEtfsWithHoldings(
    etfSymbols: List<String>? = null,
    platformFilter: Set<Platform>? = null,
  ): List<Instrument> {
    val allInstruments = getFilteredInstruments(etfSymbols)
    val withHoldings = allInstruments.filter { hasEtfHoldings(it.id) }
    log.info("${withHoldings.size} instruments have ETF holdings data: ${withHoldings.map { it.symbol }}")
    val withActivePositions = withHoldings.filter { hasActiveHoldings(it.id, platformFilter) }
    log.info("${withActivePositions.size} instruments with holdings have active positions: ${withActivePositions.map { it.symbol }}")
    return withActivePositions
  }

  private fun getAllActiveEtfs(
    etfSymbols: List<String>? = null,
    platformFilter: Set<Platform>? = null,
  ): List<Instrument> {
    val allInstruments = getFilteredInstruments(etfSymbols)
    val withActivePositions = allInstruments.filter { hasActiveHoldings(it.id, platformFilter) }
    log.info(
      "${withActivePositions.size} instruments have active positions (including without holdings): ${withActivePositions.map {
        it.symbol
      }}",
    )
    return withActivePositions
  }

  private fun getFilteredInstruments(etfSymbols: List<String>? = null): List<Instrument> {
    val lightyearInstruments = instrumentRepository.findByProviderName(ProviderName.LIGHTYEAR)
    val ftInstruments = instrumentRepository.findByProviderName(ProviderName.FT)
    val syntheticInstruments = instrumentRepository.findByProviderName(ProviderName.SYNTHETIC)
    log.info(
      "Found ${lightyearInstruments.size} LIGHTYEAR, ${ftInstruments.size} FT, " +
        "${syntheticInstruments.size} SYNTHETIC instruments",
    )
    var allInstruments = lightyearInstruments + ftInstruments + syntheticInstruments
    if (!etfSymbols.isNullOrEmpty()) {
      allInstruments = allInstruments.filter { it.symbol in etfSymbols }
      log.info("Filtered to ${allInstruments.size} instruments matching symbols: ${LogSanitizerUtil.sanitize(etfSymbols)}")
    }
    return allInstruments
  }

  private fun hasEtfHoldings(instrumentId: Long): Boolean = etfPositionRepository.findLatestPositionsByEtfId(instrumentId).isNotEmpty()

  private fun hasActiveHoldings(
    instrumentId: Long,
    platformFilter: Set<Platform>? = null,
  ): Boolean {
    val instrument = instrumentRepository.findById(instrumentId).orElse(null) ?: return false
    if (instrument.providerName == ProviderName.SYNTHETIC) {
      return hasSyntheticActiveHoldings(instrumentId)
    }
    val netQuantity = calculateNetQuantity(instrumentId, platformFilter)
    log.debug("Instrument $instrumentId has net quantity: $netQuantity")
    return netQuantity > BigDecimal.ZERO
  }

  private fun hasSyntheticActiveHoldings(syntheticEtfId: Long): Boolean {
    val positions = etfPositionRepository.findLatestPositionsByEtfId(syntheticEtfId)
    val tickers = positions.mapNotNull { it.holding.ticker }
    if (tickers.isEmpty()) return false
    val instrumentsByTicker = instrumentRepository.findBySymbolIn(tickers).associateBy { it.symbol }
    return positions.any { pos ->
      val ticker = pos.holding.ticker ?: return@any false
      val instrument = instrumentsByTicker[ticker] ?: return@any false
      calculateNetQuantity(instrument.id, null) > BigDecimal.ZERO
    }
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
    if (etf.providerName == ProviderName.SYNTHETIC) {
      return buildSyntheticHoldings(positions, etf.symbol)
    }
    val etfQuantity = calculateNetQuantity(etf.id, platformFilter)
    val etfPrice = getCurrentPrice(etf)
    val etfPlatforms = getPlatformsForInstrument(etf.id, platformFilter)
    return positions.map { position ->
      InternalHoldingData(
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
        etfSymbol = etf.symbol,
        platforms = etfPlatforms,
      )
    }
  }

  private fun buildSyntheticHoldings(
    positions: List<EtfPosition>,
    etfSymbol: String,
  ): List<InternalHoldingData> {
    val holdingValues = calculateSyntheticHoldingValues(positions)
    return holdingValues.map { h ->
      InternalHoldingData(
        holdingUuid = h.position.holding.uuid,
        ticker =
          h.position.holding.ticker
            ?.uppercase()
            ?.trim()
            ?.takeIf { it.isNotBlank() },
        name = h.position.holding.name.trim(),
        sector =
          h.position.holding.sector
            ?.trim()
            ?.takeIf { it.isNotBlank() },
        countryCode =
          h.position.holding.countryCode
            ?.trim()
            ?.takeIf { it.isNotBlank() },
        countryName =
          h.position.holding.countryName
            ?.trim()
            ?.takeIf { it.isNotBlank() },
        value = h.value,
        etfSymbol = etfSymbol,
        platforms = h.platforms,
      )
    }
  }

  private fun calculateSyntheticHoldingValues(positions: List<EtfPosition>): List<SyntheticHoldingValue> {
    val tickers = positions.mapNotNull { it.holding.ticker }
    if (tickers.isEmpty()) return emptyList()
    val instrumentsByTicker = instrumentRepository.findBySymbolIn(tickers).associateBy { it.symbol }
    return positions.mapNotNull { pos ->
      val ticker = pos.holding.ticker ?: return@mapNotNull null
      val instrument = instrumentsByTicker[ticker] ?: return@mapNotNull null
      val qty = calculateNetQuantity(instrument.id, null)
      val price = getCurrentPrice(instrument)
      val value = qty.multiply(price)
      val platforms = getPlatformsForInstrument(instrument.id, null)
      SyntheticHoldingValue(pos, value, platforms)
    }
  }

  private fun aggregateHoldings(holdings: List<InternalHoldingData>): Map<HoldingKey, HoldingValue> =
    holdings
      .groupBy { holding -> buildHoldingGroupKey(holding) }
      .entries
      .associate { (_, groupedHoldings) -> buildHoldingEntry(groupedHoldings) }

  private fun buildHoldingGroupKey(holding: InternalHoldingData): String = "name:${normalizeHoldingName(holding.name)}"

  private fun normalizeHoldingName(name: String): String = name.replace(TREZOR_SUFFIX_REGEX, "").lowercase()

  private fun buildHoldingEntry(groupedHoldings: List<InternalHoldingData>): Pair<HoldingKey, HoldingValue> {
    val key = buildHoldingKey(groupedHoldings)
    val value = buildHoldingValue(groupedHoldings)
    return key to value
  }

  private fun buildHoldingKey(groupedHoldings: List<InternalHoldingData>): HoldingKey {
    val first = groupedHoldings.firstOrNull() ?: error("Cannot build key from empty holdings list")
    val bestName = selectBestName(groupedHoldings.map { it.name })
    val longestTicker =
      groupedHoldings
        .mapNotNull { it.ticker?.takeIf { t -> t.isNotBlank() } }
      .maxByOrNull { it.length }
    return HoldingKey(
      holdingUuid = first.holdingUuid,
      ticker = longestTicker,
      name = bestName,
      sector = groupedHoldings.mapNotNull { it.sector }.maxByOrNull { it.length },
      countryCode = groupedHoldings.mapNotNull { it.countryCode }.firstOrNull(),
      countryName = groupedHoldings.mapNotNull { it.countryName }.firstOrNull(),
    )
  }

  private fun selectBestName(names: List<String>): String {
    val cleanNames = names.filter { !it.contains("(Trezor)", ignoreCase = true) }
    val bestName = cleanNames.maxByOrNull { it.length } ?: names.maxByOrNull { it.length } ?: names.first()
    return bestName.replace(TREZOR_SUFFIX_REGEX, "")
  }

  private fun buildHoldingValue(groupedHoldings: List<InternalHoldingData>) =
    HoldingValue(
      totalValue = groupedHoldings.fold(BigDecimal.ZERO) { acc, h -> acc.add(h.value) },
      etfSymbols = groupedHoldings.map { it.etfSymbol }.toMutableSet(),
      platforms = groupedHoldings.flatMap { it.platforms }.toMutableSet(),
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
    val syntheticValues = calculateSyntheticTotalValue(etfs)
    val regularValues =
      etfs
        .filter { it.providerName != ProviderName.SYNTHETIC }
        .fold(BigDecimal.ZERO) { acc, etf ->
          val quantity = calculateNetQuantity(etf.id, platformFilter)
          val price = getCurrentPrice(etf)
          acc.add(quantity.multiply(price))
        }
    return regularValues.add(syntheticValues)
  }

  private fun calculateSyntheticTotalValue(etfs: List<Instrument>): BigDecimal =
    etfs
      .filter { it.providerName == ProviderName.SYNTHETIC }
      .fold(BigDecimal.ZERO) { acc, etf ->
        val positions = etfPositionRepository.findLatestPositionsByEtfId(etf.id)
        val holdingValues = calculateSyntheticHoldingValues(positions)
        val syntheticValue = holdingValues.fold(BigDecimal.ZERO) { sum, h -> sum.add(h.value) }
        acc.add(syntheticValue)
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
      }.filter { it.totalValueEur > BigDecimal.ZERO }
      .sortedByDescending { it.totalValueEur }
  }

  @Transactional(readOnly = true)
  fun getDiagnosticData(): List<EtfDiagnosticDto> {
    val lightyearInstruments = instrumentRepository.findByProviderName(ProviderName.LIGHTYEAR)
    val ftInstruments = instrumentRepository.findByProviderName(ProviderName.FT)
    log.info("Diagnostic: Found ${lightyearInstruments.size} LIGHTYEAR and ${ftInstruments.size} FT instruments")
    val allInstruments = lightyearInstruments + ftInstruments
    return allInstruments.map { instrument -> buildDiagnosticDto(instrument) }
  }

  private fun buildDiagnosticDto(instrument: Instrument): EtfDiagnosticDto {
    val positions = etfPositionRepository.findLatestPositionsByEtfId(instrument.id)
    val transactions = transactionRepository.findAllByInstrumentId(instrument.id)
    val netQuantity = calculateNetQuantity(instrument.id, null)
    val platforms = transactions.map { it.platform.name }.distinct()
    val latestDate = positions.firstOrNull()?.snapshotDate?.toString()
    log.info(
      "Diagnostic for ${instrument.symbol}: positions=${positions.size}, " +
        "transactions=${transactions.size}, netQty=$netQuantity, platforms=$platforms",
    )
    return EtfDiagnosticDto(
      instrumentId = instrument.id,
      symbol = instrument.symbol,
      providerName = instrument.providerName,
      currentPrice = instrument.currentPrice,
      etfPositionCount = positions.size,
      latestSnapshotDate = latestDate,
      transactionCount = transactions.size,
      netQuantity = netQuantity,
      hasEtfHoldings = positions.isNotEmpty(),
      hasActivePosition = netQuantity > BigDecimal.ZERO,
      platforms = platforms,
    )
  }
}
