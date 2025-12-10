package ee.tenman.portfolio.service.etf

import ee.tenman.portfolio.domain.EtfHolding
import ee.tenman.portfolio.domain.EtfPosition
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.dto.HoldingData
import ee.tenman.portfolio.repository.EtfHoldingRepository
import ee.tenman.portfolio.repository.EtfPositionRepository
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.service.infrastructure.MinioService
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
) {
  private val log = LoggerFactory.getLogger(javaClass)

  @Cacheable("etf:holdings", key = "#etfSymbol + ':' + #date")
  fun hasHoldingsForDate(
    etfSymbol: String,
    date: LocalDate,
  ): Boolean {
    val etf = instrumentRepository.findBySymbol(etfSymbol)
    if (etf.isEmpty) return false

    val count = etfPositionRepository.countByEtfInstrumentIdAndDate(etf.get().id, date)
    return count > 0
  }

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

  private fun findOrCreateEtf(symbol: String): Instrument {
    val exactMatch = instrumentRepository.findBySymbol(symbol)
    if (exactMatch.isPresent) {
      return exactMatch.get()
    }

    val containingMatches = instrumentRepository.findBySymbolContaining(symbol)
    check(containingMatches.isNotEmpty()) { "Instrument $symbol not found in database. Please create it manually first." }

    if (containingMatches.size > 1) {
      log.warn("Multiple instruments found for symbol $symbol: ${containingMatches.map { it.symbol }}")
    }

    return containingMatches.first()
  }

  private fun findOrCreateHolding(holdingData: HoldingData): EtfHolding {
    val existingHolding = etfHoldingRepository.findByNameAndTicker(holdingData.name, holdingData.ticker)

    return if (existingHolding.isPresent) {
      val holding = existingHolding.get()
      if (holdingData.logoUrl != null) {
        uploadLogoToMinioIfNeeded(holdingData.ticker ?: holdingData.name, holdingData.logoUrl)
      }
      holding
    } else {
      log.debug(
        "Creating new holding - Name: '${holdingData.name}' (length: ${holdingData.name.length}), " +
          "Ticker: '${holdingData.ticker}' (length: ${holdingData.ticker?.length ?: 0}), " +
          "Sector: '${holdingData.sector}' (length: ${holdingData.sector?.length ?: 0}), " +
          "Logo URL: '${holdingData.logoUrl}'",
      )

      if (holdingData.logoUrl != null) {
        uploadLogoToMinioIfNeeded(holdingData.ticker ?: holdingData.name, holdingData.logoUrl)
      }

      val newHolding =
        EtfHolding(
          name = holdingData.name,
          ticker = holdingData.ticker,
          sector = holdingData.sector,
        )
      etfHoldingRepository.save(newHolding)
    }
  }

  private fun uploadLogoToMinioIfNeeded(
    symbol: String,
    logoUrl: String,
  ) {
    if (minioService.logoExists(symbol)) {
      log.debug("Logo already exists in MinIO for symbol: {}, skipping upload", symbol)
      return
    }

    try {
      val imageData = downloadImage(logoUrl)
      minioService.uploadLogo(symbol, imageData)
      log.debug("Uploaded logo to MinIO for symbol: {}", symbol)
    } catch (e: Exception) {
      log.warn("Failed to upload logo to MinIO for symbol: {}", symbol, e)
    }
  }

  private fun downloadImage(url: String): ByteArray {
    val connection =
      java.net
        .URI(url)
        .toURL()
        .openConnection()
    connection.connectTimeout = 5000
    connection.readTimeout = 5000
    return connection.getInputStream().use { it.readBytes() }
  }
}
