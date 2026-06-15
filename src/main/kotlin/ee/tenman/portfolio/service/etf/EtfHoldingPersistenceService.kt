package ee.tenman.portfolio.service.etf

import ee.tenman.portfolio.common.orNotFound
import ee.tenman.portfolio.common.orNull
import ee.tenman.portfolio.domain.AiModel
import ee.tenman.portfolio.domain.EtfHolding
import ee.tenman.portfolio.domain.EtfPosition
import ee.tenman.portfolio.domain.HoldingBlockKey
import ee.tenman.portfolio.domain.IndustrySector
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.SectorSource
import ee.tenman.portfolio.dto.HoldingData
import ee.tenman.portfolio.repository.EtfHoldingRepository
import ee.tenman.portfolio.repository.EtfPositionRepository
import ee.tenman.portfolio.repository.InstrumentRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class EtfHoldingPersistenceService(
  private val instrumentRepository: InstrumentRepository,
  private val etfHoldingRepository: EtfHoldingRepository,
  private val etfPositionRepository: EtfPositionRepository,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  @Transactional
  fun saveHoldings(
    etfSymbol: String,
    date: LocalDate,
    holdings: List<HoldingData>,
    reuseHints: Map<Int, Long> = emptyMap(),
  ): Map<String, EtfHolding> {
    val etf = findOrCreateEtf(etfSymbol)
    log.info("Saving ${holdings.size} holdings for ETF $etfSymbol on $date")
    val savedHoldings = mutableMapOf<String, EtfHolding>()
    val claimedHoldingIds = mutableSetOf<Long>()
    holdings.forEachIndexed { index, holdingData ->
      val holding = resolveHolding(holdingData, reuseHints[index]?.takeIf { it !in claimedHoldingIds })
      claimedHoldingIds.add(holding.id)
      savedHoldings[holdingData.name] = holding
      upsertPosition(etf, holding, date, holdingData)
    }
    log.info("Successfully saved ${holdings.size} holdings for ETF $etfSymbol")
    return savedHoldings
  }

  private fun resolveHolding(
    holdingData: HoldingData,
    reuseHoldingId: Long?,
  ): EtfHolding {
    val hinted = reuseHoldingId?.let { etfHoldingRepository.findById(it).orNull() }
    if (hinted != null) {
      applyMissingFields(hinted, holdingData.ticker, holdingData.sector, holdingData.countryCode, holdingData.countryName)
      return hinted
    }
    return findOrCreateHolding(
      holdingData.name,
      holdingData.ticker,
      holdingData.sector,
      holdingData.countryCode,
      holdingData.countryName,
    )
  }

  private fun upsertPosition(
    etf: Instrument,
    holding: EtfHolding,
    date: LocalDate,
    holdingData: HoldingData,
  ) {
    val existingPosition =
      etfPositionRepository.findByEtfInstrumentAndHoldingIdAndSnapshotDate(
        etfInstrument = etf,
        holdingId = holding.id,
        snapshotDate = date,
      )
    val position =
      existingPosition ?: EtfPosition(
        etfInstrument = etf,
        holding = holding,
        snapshotDate = date,
        weightPercentage = holdingData.weight,
        positionRank = holdingData.rank,
      )
    if (existingPosition != null) {
      position.weightPercentage = holdingData.weight
      position.positionRank = holdingData.rank
    }
    etfPositionRepository.save(position)
  }

  @Transactional
  fun findOrCreateHolding(
    name: String,
    ticker: String?,
    sector: String? = null,
    countryCode: String? = null,
    countryName: String? = null,
  ): EtfHolding {
    val existing = etfHoldingRepository.findByNameIgnoreCase(name)
    if (existing != null) {
      applyMissingFields(existing, ticker, sector, countryCode, countryName)
      return existing
    }
    log.debug("Creating new holding: name='$name', ticker='$ticker'")
    val canonicalSector = sector?.let { IndustrySector.fromDisplayName(it) }
    return etfHoldingRepository.save(
      EtfHolding(
        name = name,
        ticker = ticker,
        sector = canonicalSector,
        sectorSource = canonicalSector?.let { SectorSource.LIGHTYEAR },
        countryCode = countryCode,
        countryName = countryName,
      ),
    )
  }

  @Transactional(readOnly = true)
  fun findByNameBlockKey(name: String): List<EtfHolding> {
    val blockKey = HoldingBlockKey.of(name)
    if (blockKey.isEmpty()) return emptyList()
    return etfHoldingRepository.findByNameBlockKey(blockKey)
  }

  @Transactional(readOnly = true)
  fun findByTicker(ticker: String): List<EtfHolding> = etfHoldingRepository.findByTicker(ticker)

  private fun applyMissingFields(
    holding: EtfHolding,
    ticker: String?,
    sector: String?,
    countryCode: String?,
    countryName: String?,
  ) {
    updateTickerIfMissing(holding, ticker)
    updateSectorFromSourceIfMissing(holding, sector)
    updateCountryFromSourceIfMissing(holding, countryCode, countryName)
  }

  fun hasHoldingsForDate(
    etfSymbol: String,
    date: LocalDate,
  ): Boolean =
    instrumentRepository
      .findBySymbol(etfSymbol)
      .map { etfPositionRepository.countByEtfInstrumentIdAndDate(it.id, date) > 0 }
      .orElse(false)

  @Transactional
  fun saveHolding(holding: EtfHolding): EtfHolding = etfHoldingRepository.save(holding)

  @Transactional(readOnly = true)
  fun findUnclassifiedHoldingIds(): List<Long> =
    etfHoldingRepository
      .findUnclassifiedSectorHoldings()
      .map { it.id }

  @Transactional(readOnly = true)
  fun findUnclassifiedByCountryHoldingIds(maxAttempts: Int = MAX_COUNTRY_FETCH_ATTEMPTS): List<Long> =
    etfHoldingRepository
      .findUnclassifiedCountryHoldings(maxAttempts)
      .map { it.id }

  companion object {
    const val MAX_COUNTRY_FETCH_ATTEMPTS = 3
  }

  @Transactional(readOnly = true)
  fun findById(id: Long): EtfHolding? = etfHoldingRepository.findById(id).orNull()

  @Transactional(readOnly = true)
  fun findEtfNamesForHolding(holdingId: Long): List<String> = etfHoldingRepository.findEtfNamesForHolding(holdingId)

  @Transactional(readOnly = true)
  fun findEtfNamesForHoldings(holdingIds: List<Long>): Map<Long, List<String>> {
    if (holdingIds.isEmpty()) return emptyMap()
    return etfHoldingRepository
      .findEtfNamesForHoldings(holdingIds)
      .groupBy({ (it[0] as Long) }, { it[1] as String })
      .mapValues { it.value.distinct() }
  }

  @Transactional(readOnly = true)
  fun findAllByIds(ids: List<Long>): List<EtfHolding> = etfHoldingRepository.findAllById(ids)

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  fun updateSector(
    holdingId: Long,
    sector: IndustrySector,
    classifiedByModel: AiModel? = null,
  ) {
    val holding = etfHoldingRepository.findById(holdingId).orNotFound(holdingId)
    holding.sector = sector
    holding.classifiedByModel = classifiedByModel
    holding.sectorSource = SectorSource.LLM
    etfHoldingRepository.save(holding)
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  fun updateCountry(
    holdingId: Long,
    countryCode: String,
    countryName: String,
    classifiedByModel: AiModel? = null,
  ) {
    val holding = etfHoldingRepository.findById(holdingId).orNotFound(holdingId)
    holding.countryCode = countryCode
    holding.countryName = countryName
    holding.countryClassifiedByModel = classifiedByModel
    etfHoldingRepository.save(holding)
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  fun incrementCountryFetchAttempts(holdingId: Long) {
    val holding = etfHoldingRepository.findById(holdingId).orNotFound(holdingId)
    holding.countryFetchAttempts++
    etfHoldingRepository.save(holding)
    log.info("Incremented country fetch attempts for holding id=$holdingId to ${holding.countryFetchAttempts}")
  }

  private fun findOrCreateEtf(symbol: String): Instrument =
    instrumentRepository.findBySymbol(symbol).orElseGet {
      val matches = instrumentRepository.findBySymbolContaining(symbol)
      check(matches.isNotEmpty()) { "Instrument $symbol not found in database" }
      if (matches.size > 1) log.warn("Multiple instruments found for symbol $symbol: ${matches.map { it.symbol }}")
      matches.first()
    }

  private fun updateSectorFromSourceIfMissing(
    holding: EtfHolding,
    sourceSector: String?,
  ) {
    if (holding.sector != null) return
    val canonicalSector = sourceSector?.let { IndustrySector.fromDisplayName(it) } ?: return
    log.info("Updating sector from source for '${holding.name}': ${canonicalSector.displayName}")
    holding.sector = canonicalSector
    holding.sectorSource = SectorSource.LIGHTYEAR
  }

  private fun updateCountryFromSourceIfMissing(
    holding: EtfHolding,
    sourceCountryCode: String?,
    sourceCountryName: String?,
  ) {
    if (!holding.countryCode.isNullOrBlank()) return
    if (sourceCountryCode.isNullOrBlank() || sourceCountryName.isNullOrBlank()) return
    log.info("Updating country from source for '${holding.name}': $sourceCountryCode ($sourceCountryName)")
    holding.countryCode = sourceCountryCode
    holding.countryName = sourceCountryName
  }

  private fun updateTickerIfMissing(
    holding: EtfHolding,
    ticker: String?,
  ) {
    if (ticker.isNullOrBlank()) return
    if (!holding.ticker.isNullOrBlank()) return
    log.info("Updating ticker for '${holding.name}': $ticker")
    holding.ticker = ticker
  }
}
