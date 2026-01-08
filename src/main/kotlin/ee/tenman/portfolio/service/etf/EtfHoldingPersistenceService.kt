package ee.tenman.portfolio.service.etf

import ee.tenman.portfolio.domain.AiModel
import ee.tenman.portfolio.domain.EtfHolding
import ee.tenman.portfolio.domain.EtfPosition
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
  ): Map<String, EtfHolding> {
    val etf = findOrCreateEtf(etfSymbol)
    log.info("Saving ${holdings.size} holdings for ETF $etfSymbol on $date")
    val allHoldings = loadOrCreateHoldings(holdings)
    updateExistingHoldingsMetadata(allHoldings.values.toList(), holdings)
    savePositions(etf, date, holdings, allHoldings)
    log.info("Successfully saved ${holdings.size} holdings for ETF $etfSymbol")
    return holdings.associate { it.name to allHoldings[it.name.lowercase()]!! }
  }

  private fun loadOrCreateHoldings(holdings: List<HoldingData>): Map<String, EtfHolding> {
    val holdingNames = holdings.map { it.name.lowercase() }
    val existingHoldings = etfHoldingRepository.findAllByNamesIgnoreCase(holdingNames).associateBy { it.name.lowercase() }
    val createdHoldings = createMissingHoldings(holdings, existingHoldings)
    return existingHoldings + createdHoldings
  }

  private fun createMissingHoldings(
    holdings: List<HoldingData>,
    existingHoldings: Map<String, EtfHolding>,
  ): Map<String, EtfHolding> {
    val newHoldingData = holdings.filterNot { existingHoldings.containsKey(it.name.lowercase()) }
    if (newHoldingData.isEmpty()) return emptyMap()
    val newHoldings =
      newHoldingData.map {
        EtfHolding(
          name = it.name,
          ticker = it.ticker,
          sector = it.sector,
          sectorSource = it.sector?.let { SectorSource.LIGHTYEAR },
        )
      }
    return etfHoldingRepository.saveAll(newHoldings).associateBy { it.name.lowercase() }
  }

  private fun updateExistingHoldingsMetadata(
    holdings: List<EtfHolding>,
    holdingsData: List<HoldingData>,
  ) {
    val dataByName = holdingsData.associateBy { it.name.lowercase() }
    holdings.forEach { holding ->
      val matchingData = dataByName[holding.name.lowercase()] ?: return@forEach
      updateTickerIfMissing(holding, matchingData.ticker)
      updateSectorFromSourceIfMissing(holding, matchingData.sector)
    }
  }

  private fun savePositions(
    etf: Instrument,
    date: LocalDate,
    holdings: List<HoldingData>,
    allHoldings: Map<String, EtfHolding>,
  ) {
    val existingPositions = etfPositionRepository.findByEtfInstrumentIdAndSnapshotDate(etf.id, date).associateBy { it.holding.id }
    val positions = holdings.map { buildPosition(it, allHoldings, existingPositions, etf, date) }
    etfPositionRepository.saveAll(positions)
  }

  private fun buildPosition(
    holdingData: HoldingData,
    allHoldings: Map<String, EtfHolding>,
    existingPositions: Map<Long, EtfPosition>,
    etf: Instrument,
    date: LocalDate,
  ): EtfPosition {
    val holding = allHoldings[holdingData.name.lowercase()]!!
    return existingPositions[holding.id]?.apply {
      weightPercentage = holdingData.weight
      positionRank = holdingData.rank
    } ?: EtfPosition(
      etfInstrument = etf,
      holding = holding,
      snapshotDate = date,
      weightPercentage = holdingData.weight,
      positionRank = holdingData.rank,
    )
  }

  @Transactional
  fun findOrCreateHolding(
    name: String,
    ticker: String?,
    sector: String? = null,
  ): EtfHolding {
    val existing = etfHoldingRepository.findByNameIgnoreCase(name)
    if (existing != null) {
      updateTickerIfMissing(existing, ticker)
      updateSectorFromSourceIfMissing(existing, sector)
      return existing
    }
    log.debug("Creating new holding: name='$name', ticker='$ticker'")
    return etfHoldingRepository.save(
      EtfHolding(
        name = name,
        ticker = ticker,
        sector = sector,
        sectorSource = sector?.let { SectorSource.LIGHTYEAR },
      ),
    )
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
      .findUnclassifiedSectorHoldingsForCurrentPortfolio()
      .map { it.id }

  @Transactional(readOnly = true)
  fun findUnclassifiedByCountryHoldingIds(maxAttempts: Int = MAX_COUNTRY_FETCH_ATTEMPTS): List<Long> =
    etfHoldingRepository
      .findUnclassifiedCountryHoldingsForCurrentPortfolio(maxAttempts)
      .map { it.id }

  companion object {
    const val MAX_COUNTRY_FETCH_ATTEMPTS = 3
  }

  @Transactional(readOnly = true)
  fun findById(id: Long): EtfHolding? = etfHoldingRepository.findById(id).orElse(null)

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
    sector: String,
    classifiedByModel: AiModel? = null,
  ) {
    val holding =
      etfHoldingRepository.findById(holdingId).orElseThrow {
        IllegalStateException("EtfHolding not found with id=$holdingId")
      }
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
    val holding =
      etfHoldingRepository.findById(holdingId).orElseThrow {
        IllegalStateException("EtfHolding not found with id=$holdingId")
      }
    holding.countryCode = countryCode
    holding.countryName = countryName
    holding.countryClassifiedByModel = classifiedByModel
    etfHoldingRepository.save(holding)
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  fun incrementCountryFetchAttempts(holdingId: Long) {
    val holding =
      etfHoldingRepository.findById(holdingId).orElseThrow {
        IllegalStateException("EtfHolding not found with id=$holdingId")
      }
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
    if (holding.sector.isNullOrBlank() && !sourceSector.isNullOrBlank()) {
      log.info("Updating sector from source for '${holding.name}': $sourceSector")
      holding.sector = sourceSector
      holding.sectorSource = SectorSource.LIGHTYEAR
    }
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
