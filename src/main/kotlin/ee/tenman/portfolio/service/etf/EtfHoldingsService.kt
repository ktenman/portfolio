package ee.tenman.portfolio.service.etf

import ee.tenman.portfolio.domain.EtfHolding
import ee.tenman.portfolio.domain.EtfPosition
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.SectorSource
import ee.tenman.portfolio.dto.HoldingData
import ee.tenman.portfolio.repository.EtfHoldingRepository
import ee.tenman.portfolio.repository.EtfPositionRepository
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.service.infrastructure.MinioService
import ee.tenman.portfolio.service.logo.LogoFallbackService
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class EtfHoldingsService(
  private val instrumentRepository: InstrumentRepository,
  private val etfHoldingRepository: EtfHoldingRepository,
  private val etfPositionRepository: EtfPositionRepository,
  private val minioService: MinioService,
  private val logoFallbackService: LogoFallbackService,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  @Cacheable("etf:holdings", key = "#etfSymbol + ':' + #date")
  fun hasHoldingsForDate(
    etfSymbol: String,
    date: LocalDate,
  ): Boolean =
    instrumentRepository
      .findBySymbol(etfSymbol)
      .map { etfPositionRepository.countByEtfInstrumentIdAndDate(it.id, date) > 0 }
      .orElse(false)

  @Transactional
  fun saveHoldings(
    etfSymbol: String,
    date: LocalDate,
    holdings: List<HoldingData>,
  ) {
    val etf = findOrCreateEtf(etfSymbol)

    log.info("Saving ${holdings.size} holdings for ETF $etfSymbol on $date")

    holdings.forEach { holdingData ->
      val holding = findOrCreateHolding(holdingData)

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
  }

  private fun findOrCreateEtf(symbol: String): Instrument =
    instrumentRepository.findBySymbol(symbol).orElseGet {
      val matches = instrumentRepository.findBySymbolContaining(symbol)
      check(matches.isNotEmpty()) { "Instrument $symbol not found in database" }
      if (matches.size > 1) log.warn("Multiple instruments found for symbol $symbol: ${matches.map { it.symbol }}")
      matches.first()
    }

  private fun findOrCreateHolding(holdingData: HoldingData): EtfHolding {
    val existing = etfHoldingRepository.findByNameIgnoreCase(holdingData.name)
    if (existing.isPresent) {
      val holding = existing.get()
      updateTickerIfExtracted(holding, holdingData.ticker)
      updateSectorFromSourceIfMissing(holding, holdingData.sector)
      uploadLogoWithFallback(holding, holdingData.logoUrl)
      return holding
    }
    log.debug("Creating new holding: name='${holdingData.name}', ticker='${holdingData.ticker}'")
    val newHolding =
      etfHoldingRepository.save(
      EtfHolding(
        name = holdingData.name,
        ticker = holdingData.ticker,
        sector = holdingData.sector,
        sectorSource = holdingData.sector?.let { SectorSource.LIGHTYEAR },
      ),
    )
    uploadLogoWithFallback(newHolding, holdingData.logoUrl)
    return newHolding
  }

  private fun updateSectorFromSourceIfMissing(
    holding: EtfHolding,
    sourceSector: String?,
  ) {
    if (holding.sector.isNullOrBlank() && !sourceSector.isNullOrBlank()) {
      log.info("Updating sector from source for '${holding.name}': $sourceSector")
      holding.sector = sourceSector
      holding.sectorSource = SectorSource.LIGHTYEAR
      etfHoldingRepository.save(holding)
    }
  }

  private fun uploadLogoWithFallback(
    holding: EtfHolding,
    lightyearLogoUrl: String?,
  ) {
    if (holding.logoFetched) return
    if (minioService.logoExists(holding.id)) {
      holding.logoFetched = true
      etfHoldingRepository.save(holding)
      return
    }
    val result =
      runCatching {
        logoFallbackService.fetchLogo(
          companyName = holding.name,
          existingTicker = holding.ticker,
          lightyearLogoUrl = lightyearLogoUrl,
        )
      }.onFailure {
        log.warn("Logo fallback failed for holding: ${holding.name}", it)
      }.getOrNull()
    if (result == null) {
      holding.logoFetched = true
      etfHoldingRepository.save(holding)
      return
    }
    updateTickerIfExtracted(holding, result.ticker)
    runCatching { minioService.uploadLogo(holding.id, result.imageData) }
      .onSuccess {
        log.info("Uploaded logo for '${holding.name}' from ${result.source}")
        holding.logoFetched = true
        holding.logoSource = result.source
        etfHoldingRepository.save(holding)
      }.onFailure { log.warn("Failed to upload logo to MinIO for: ${holding.name}", it) }
  }

  private fun updateTickerIfExtracted(
    holding: EtfHolding,
    extractedTicker: String?,
  ) {
    if (extractedTicker.isNullOrBlank()) return
    if (!holding.ticker.isNullOrBlank()) return
    log.info("Updating ticker for '${holding.name}': $extractedTicker")
    holding.ticker = extractedTicker
    etfHoldingRepository.save(holding)
  }
}
