package ee.tenman.portfolio.service.etf

import ee.tenman.portfolio.domain.EtfHolding
import ee.tenman.portfolio.domain.LogoSource
import ee.tenman.portfolio.dto.HoldingData
import ee.tenman.portfolio.service.infrastructure.ImageDownloadService
import ee.tenman.portfolio.service.infrastructure.ImageProcessingService
import ee.tenman.portfolio.service.infrastructure.MinioService
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class EtfHoldingService(
  private val etfHoldingPersistenceService: EtfHoldingPersistenceService,
  private val minioService: MinioService,
  private val imageDownloadService: ImageDownloadService,
  private val imageProcessingService: ImageProcessingService,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  @Cacheable("etf:holdings", key = "#etfSymbol + ':' + #date")
  fun hasHoldingsForDate(
    etfSymbol: String,
    date: LocalDate,
  ): Boolean = etfHoldingPersistenceService.hasHoldingsForDate(etfSymbol, date)

  fun saveHoldings(
    etfSymbol: String,
    date: LocalDate,
    holdings: List<HoldingData>,
  ) {
    val savedHoldings = etfHoldingPersistenceService.saveHoldings(etfSymbol, date, holdings)
    holdings.forEach { holdingData ->
      val holding = savedHoldings[holdingData.name] ?: return@forEach
      downloadLightyearLogo(holding, holdingData.logoUrl)
    }
  }

  fun findOrCreateHolding(
    name: String,
    ticker: String?,
    sector: String? = null,
  ): EtfHolding = etfHoldingPersistenceService.findOrCreateHolding(name, ticker, sector)

  private fun downloadLightyearLogo(
    holding: EtfHolding,
    lightyearLogoUrl: String?,
  ) {
    if (lightyearLogoUrl.isNullOrBlank()) return
    if (holding.logoSource == LogoSource.LIGHTYEAR) return
    val imageData =
      runCatching { imageDownloadService.download(lightyearLogoUrl) }
        .onFailure { log.debug("Failed to download Lightyear logo for ${holding.name}: ${it.message}") }
        .getOrNull() ?: return
    val processedImage = imageProcessingService.resizeToMaxDimension(imageData)
    runCatching { minioService.uploadLogo(holding.id, processedImage) }
      .onSuccess {
        log.info("Uploaded Lightyear logo for: ${holding.name}")
        holding.logoSource = LogoSource.LIGHTYEAR
        etfHoldingPersistenceService.saveHolding(holding)
      }.onFailure { log.warn("Failed to upload logo for ${holding.name}: ${it.message}") }
  }
}
