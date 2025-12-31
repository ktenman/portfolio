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
    val savedHoldings = mutableMapOf<String, EtfHolding>()
    holdings.forEach { holdingData ->
      val holding = findOrCreateHolding(holdingData.name, holdingData.ticker, holdingData.sector)
      savedHoldings[holdingData.name] = holding
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
    log.info("Successfully saved ${holdings.size} holdings for ETF $etfSymbol")
    return savedHoldings
  }

  @Transactional
  fun findOrCreateHolding(
    name: String,
    ticker: String?,
    sector: String? = null,
  ): EtfHolding {
    val existing = etfHoldingRepository.findByNameIgnoreCase(name)
    if (existing.isPresent) {
      val holding = existing.get()
      updateTickerIfMissing(holding, ticker)
      updateSectorFromSourceIfMissing(holding, sector)
      return holding
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
  fun findUnclassifiedByCountryHoldingIds(): List<Long> =
    etfHoldingRepository
      .findUnclassifiedCountryHoldingsForCurrentPortfolio()
      .map { it.id }

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
