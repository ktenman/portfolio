package ee.tenman.portfolio.service.etf

import ee.tenman.portfolio.domain.EtfHolding
import ee.tenman.portfolio.domain.EtfPosition
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.SectorSource
import ee.tenman.portfolio.dto.HoldingData
import ee.tenman.portfolio.repository.EtfHoldingRepository
import ee.tenman.portfolio.repository.EtfPositionRepository
import ee.tenman.portfolio.repository.InstrumentRepository
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class EtfHoldingsPersistenceService(
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
